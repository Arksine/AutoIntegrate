# 1 "d:\\Development\\android_projects\\AutoIntegrate\\arduino_sketches\\AutoIntegrate-STM32\\AutoIntegrate-STM32.ino"
# 1 "d:\\Development\\android_projects\\AutoIntegrate\\arduino_sketches\\AutoIntegrate-STM32\\AutoIntegrate-STM32.ino"
/**
 * AutoIntegrate-STM32
 *
 * Arudino sketch to communicate with Android AutoIntegrate applicaton.
 * This particular sketch is initially designed for STM32 boards (maple mini),
 * but should work with Atmel AVR based MCUs as well
 */

# 10 "d:\\Development\\android_projects\\AutoIntegrate\\arduino_sketches\\AutoIntegrate-STM32\\AutoIntegrate-STM32.ino" 2
# 11 "d:\\Development\\android_projects\\AutoIntegrate\\arduino_sketches\\AutoIntegrate-STM32\\AutoIntegrate-STM32.ino" 2
# 12 "d:\\Development\\android_projects\\AutoIntegrate\\arduino_sketches\\AutoIntegrate-STM32\\AutoIntegrate-STM32.ino" 2

// Set your own unique ID, it should be 8 alphanumeric digits
const char *mcuId = "TEST1234";

enum AudioInput { HD_RADIO, AUX };

Average<unsigned int> ave(20);

ButtonCB button(PA11, Button::PULL_UP, 22);

AudioInput audio_input_selection = HD_RADIO;

// TODO: change shorts to ints, should be able to handle the different sizes
unsigned short btn_analog_value = 0;
unsigned short analog_dimmer_reading = 0;
unsigned long reverse_start_time = 0;

bool isStarted = false;
bool isHolding = false;
bool inReverse = false;
bool isDimmerOn = false;
bool analogDimmerEnabled = false;
bool radioDtrOn = false;
bool radioRtsOn = false;

uint8_t inBuffer[256]; // Maximum buffer size of 40 is probably way too large
uint8_t bufIndex = 0;
uint8_t packetLength = 0;
int checksum = 0;
bool isLengthByte = false;
bool isValidPacket = false;
bool isEscaped = false;

void onResistivePress(const Button& b) {
  for (int i = 0; i < 20; i++) {
    ave.push(analogRead(PA0));
  }
  btn_analog_value = ave.mean();
  ave.clear();
}

void onResistiveClick(const Button& b) {
  sendPacketToPc(0x03, 0x01, (byte *)&btn_analog_value,
                 sizeof(btn_analog_value));
}

void onResistiveHold(const Button& b) {
  isHolding = true;
  sendPacketToPc(0x04, 0x01, (byte *)&btn_analog_value,
                 sizeof(btn_analog_value));
}

void onResistiveRelease(const Button& b) {
  if (isHolding) {
    isHolding = false;
    sendPacketToPc(0x05, 0x01, (byte *)&btn_analog_value,
                   sizeof(btn_analog_value));
  }
}

void setup() {
  pinMode(PA0, INPUT_ANALOG);
  pinMode(PA1, INPUT_ANALOG);
  pinMode(6, 0x1);


  pinMode(PB12, 0x1);
  pinMode(PB13, 0x1);



  pinMode(1, 0x1);


  button.setHoldThreshold(800);
  button.pressHandler(onResistivePress);
  button.clickHandler(onResistiveClick);
  button.holdHandler(onResistiveHold);
  button.releaseHandler(onResistiveRelease);

  Serial.begin(230400);

  while (!Serial);
  Serial.flush();
}

void loop() {
  // check for command data
  parseIncoming();

  if (isStarted) {

    processRadioIncoming();


    // check for reverse
    processReverse();

    // check dimmer
    processDimmer();

    // check for button press
    button.process();
  }
}

void parseIncoming() {
  while (Serial.available() > 0) {
    byte b = Serial.read();

    if (b == 0xF1) {
      // header start
      isValidPacket = true;
      isLengthByte = true;
      isEscaped = false;

      checksum = 0xF1;
      packetLength = 0;
      bufIndex = 0;
    } else if (!isValidPacket) {
      // TODO: Send log back to device, the packet is invalid
    } else if ((b == 0x1A) && !isEscaped) {
      isEscaped = true;
    } else {
      // Unescape byte if necessary
      if (isEscaped) {
        isEscaped = false;

        if (b == 0x20) {
          b = 0xF1;
        }
      }

      if (isLengthByte) {
        isLengthByte = false;
        packetLength = b;
        checksum += packetLength;
      } else if (bufIndex == packetLength) {
        // This is the checksum
        uint8_t calcSum = checksum % 256;

        if (calcSum == b) {
          executeCommand();
        } else {
          // TODO: Invalid Checksum send log
        }

        // Packet if finished, not valid until a new header is received
        isValidPacket = false;
      } else {
        // part of the packet
        inBuffer[bufIndex] = b;
        bufIndex++;
        checksum += b;
      }
    }
  }
}

/**
 * Executes a given command.
 */
void executeCommand() {
  byte command = inBuffer[0];

  switch (command) {
  case 0x01:
    processStartCommand();
    break;

  case 0x02:
    processStopCommand();
    break;

  case 0x03:
    sendPacketToPc(0x02, 0x03, (byte *)mcuId, strlen(mcuId));
    break;

  case 0x04:
    setDimmerAnalog();
    break;

  case 0x05:
    setDimmerDigital();
    break;

  case 0x06:
    setSourceHd();
    break;

  case 0x07:
    setSourceAux();
    break;

  case 0x08:

    sendRadioPacket();

    break;

  case 0x09:

    setDtr((inBuffer[1] == 0x01));

    break;

  case 0x0A:

    setRts((inBuffer[1] == 0x01));

    break;

  case 0x0B:
    processCustom();
    break;

  default: {
    // Unknown command, send it back to the device log
    // TODO: convert the hex buffer to string and send via log
    const char str[] = "Unknown Command Received";
    sendPacketToPc(0x08, 0x03, (byte *)str, strlen(str));
  }
  }
}

/**
 * Process custom commands received from the device here.  The commands
 * type should be defined in defintions.h, it will be the 2nd byte in the
 * buffer.  That byte is the basis of the switch statement.
 */
void processCustom() {
  switch (inBuffer[1]) {}
}

void processStartCommand() {
  delay(1000); // delay one sec so the app is ready
               // to receive

  if (isStarted) {
    // reset variables so commands can be resent if necessary
    isDimmerOn = false;
    inReverse = false;
    reverse_start_time = 0;
    analog_dimmer_reading = 0;
  }

  else {
    Serial3.begin(115200);

    while (!Serial3);
    Serial3.flush();
  }


  // TODO: I could send back some status here instead of ID, since I have
  // a metho for requesting ID.
  sendPacketToPc(0x01, 0x03, (byte *)mcuId, strlen(mcuId));
  isStarted = true;


  digitalWrite(1, 0x1);

}

void processStopCommand() {
  isStarted = false;
  isDimmerOn = false;
  inReverse = false;
  reverse_start_time = 0;
  analog_dimmer_reading = 0;



  if (radioDtrOn) {
    radioDtrOn = false;
    digitalWrite(PB12, 0x0);
  }

  if (radioRtsOn) {
    radioRtsOn = false;
    digitalWrite(PB13, 0x0);
  }
  Serial3.end();



  digitalWrite(1, 0x0);

}

void setDimmerAnalog() {
  analogDimmerEnabled = true;
  analog_dimmer_reading = 0;
}

void setDimmerDigital() {
  analogDimmerEnabled = false;
}

void setSourceHd() {
  if (audio_input_selection != HD_RADIO) {
    digitalWrite(6, 0x0);
    audio_input_selection = HD_RADIO;

    const char str[] = "HD_RADIO_INPUT_SET";
    sendPacketToPc(0x08, 0x03, (byte *)str, strlen(str));
  }
}

void setSourceAux() {
  if (audio_input_selection != AUX) {
    digitalWrite(6, 0x1);
    audio_input_selection = AUX;

    const char str[] = "AUX_INPUT_SET";
    sendPacketToPc(0x08, 0x03, (byte *)str, strlen(str));
  }
}


void processRadioIncoming() {
  while (Serial3.available() > 0) {
    byte b = Serial3.read();
    writeRadioByte(b);
  }
}

void sendRadioPacket() {
  uint8_t *radioBuf = inBuffer + 1; // Radio packet starts after the command
  int radioLength = packetLength - 1; // buffer length minus command

  Serial3.write(radioBuf, radioLength);
}

void setDtr(bool status) {
  if (status) {
    // raise dtr
    if (!radioDtrOn) {
      digitalWrite(PB12, 0x1);
      radioDtrOn = true;
    }
  } else {
    // lower dtr
    if (radioDtrOn) {
      digitalWrite(PB12, 0x0);
      radioDtrOn = false;
    }
  }
}

void setRts(bool status) {
  if (status) {
    // raise rts
    if (!radioRtsOn) {
      digitalWrite(PB13, 0x1);
      radioRtsOn = true;
    }
  } else {
    // lower rts
    if (radioRtsOn) {
      digitalWrite(PB13, 0x0);
      radioRtsOn = false;
    }
  }
}



void processReverse() {
  if (digitalRead(PA15) == 0x1) {
    if (!inReverse) {
      if (reverse_start_time == 0) {
        reverse_start_time = millis();
      } else if (millis() >= reverse_start_time + 500) {
        inReverse = true;
        sendPacketToPc(0x07, 0x04, (byte *)&inReverse,
                       sizeof(inReverse));
      }
    } else {
      inReverse = false;
      reverse_start_time = 0;
      sendPacketToPc(0x07, 0x04, (byte *)&inReverse,
                     sizeof(inReverse));
    }
  }
}

void processDimmer() {
  if (digitalRead(PA12) == 0x1) {
    if (!isDimmerOn) {
      isDimmerOn = true;
      sendPacketToPc(0x06, 0x04, (byte *)&isDimmerOn,
                     sizeof(isDimmerOn));
    } else {
      // Analog read here, but only if analog is enabled
      if (analogDimmerEnabled) {
        unsigned int reading = analogRead(PA1);

        if ((reading > analog_dimmer_reading + 100) ||
            (reading < analog_dimmer_reading - 100)) {
          analog_dimmer_reading = reading;
          sendPacketToPc(0x06, 0x01, (byte *)&analog_dimmer_reading,
                         sizeof(analog_dimmer_reading));
        }
      }
    }
  } else {
    if (isDimmerOn) {
      isDimmerOn = false;
      sendPacketToPc(0x06, 0x04, (byte *)&isDimmerOn,
                     sizeof(isDimmerOn));
    }
  }
}

void sendPacketToPc(byte cmd,
                    byte data_type,
                    const byte *data,
                    short data_length) {
  if (data_length > 253) {
    // TODO: packet is too big
  }

  byte length = data_length + 2;

  short checksum = 0xF1 + length;

  writeMcuByte(0xF1); // Start header
  writeEscapedByte(length); // Length of command (not including escape bytes
                               // and
  // header)
  writeEscapedByte(cmd); // command
  checksum += cmd;

  writeEscapedByte(data_type); // data type
  checksum += data_type;

  for (size_t i = 0; i < data_length; i++) {
    writeEscapedByte(data[i]);
    checksum += data[i];
  }

  byte cksum = checksum % 256;
  writeEscapedByte(cksum);
}

// writes a byte to serial, checking to see if it should be escaped.  This uses
// 0x1A (ascii substitute) instead of 0x1B (Esc) for escaping, so as not to
// confuse
// with radio packets
void writeEscapedByte(byte b) {
  if (b == 0x1A) {
    // escape 0x1A as 0x1A
    writeMcuByte(0x1A);
    writeMcuByte(0x1A);
  } else if (b == 0xF1) {
    // escape 0xF1 as 0x20
    writeMcuByte(0x1A);
    writeMcuByte(0x20);
  } else {
    writeMcuByte(b);
  }
}

void writeMcuByte(byte data) {
  byte out[] = { 0x00, data };

  Serial.write(out, 2);
}

void writeRadioByte(byte data) {
  byte out[] = { 0x01, data };

  Serial.write(out, 2);
}

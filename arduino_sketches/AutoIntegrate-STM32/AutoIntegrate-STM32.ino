/**
 * AutoIntegrate-STM32
 *
 * Arudino sketch to communicate with Android AutoIntegrate applicaton.
 * This particular sketch is initially designed for STM32 boards (maple mini),
 * but should work with Atmel AVR based MCUs as well
 */

#include <Average.h>
#include <Button.h>
#include "definitions.h"

// Set your own unique ID, it should be 8 alphanumeric digits
const char *mcuId = "TEST1234";

enum AudioInput { HD_RADIO, AUX };

Average<unsigned int> ave(SMOOTH);

ButtonCB button(BUTTON_DIGITAL_PIN, Button::PULL_DOWN, BUTTON_DEBOUNCE_DELAY);

AudioInput audio_input_selection = HD_RADIO;

uint16_t btn_analog_value      = 0;
uint16_t analog_dimmer_reading = 0;
uint32_t reverse_start_time    = 0;

bool isStarted           = false;
bool isHolding           = false;
bool inReverse           = false;
bool isDimmerOn          = false;
bool analogDimmerEnabled = false;
bool radioConnected      = false;
bool radioDtrOn          = false;
bool radioRtsOn          = false;

uint8_t inBuffer[256]; // Maximum buffer size of 40 is probably way too large
uint8_t bufIndex      = 0;
uint8_t packetLength  = 0;
uint16_t checksum      = 0;
bool    isLengthByte  = false;
bool    isValidPacket = false;
bool    isEscaped     = false;

void onResistivePress(const Button& b) {
  for (uint8_t i = 0; i < SMOOTH; i++) {
    ave.push(analogRead(BUTTON_ANALOG_PIN));
  }
  btn_analog_value = ave.mean();
  ave.clear();
}

void onResistiveClick(const Button& b) {
  sendPacketToPc(CMD_CLICK, (byte *)&btn_analog_value,
                 sizeof(btn_analog_value));
}

void onResistiveHold(const Button& b) {
  isHolding = true;
  sendPacketToPc(CMD_HOLD, (byte *)&btn_analog_value,
                 sizeof(btn_analog_value));
}

void onResistiveRelease(const Button& b) {
  if (isHolding) {
    isHolding = false;
    sendPacketToPc(CMD_RELEASE, (byte *)&btn_analog_value,
                   sizeof(btn_analog_value));
  }
}

void setup() {
  pinMode(REVERSE_PIN, INPUT_PULLUP);
  pinMode(DIMMER_DIGITAL_PIN, INPUT_PULLUP);
  pinMode(BUTTON_ANALOG_PIN, INPUT_ANALOG);
  pinMode(DIMMER_ANALOG_PIN, INPUT_ANALOG);
  pinMode(AUDIO_SOURCE_PIN,  OUTPUT);

  #if defined(HDRadioSerial)
  pinMode(RADIO_DTR_PIN,     OUTPUT);
  pinMode(RADIO_RTS_PIN,     OUTPUT);
  HDRadioSerial.begin(115200);
  HDRadioSerial.flush();
  radioConnected = true;
  #endif // if defined(HDRadioSerial)

  #ifdef LED_PIN
  pinMode(PB1, OUTPUT);
  #endif // ifdef LED_PIN

  button.setHoldThreshold(BUTTON_HOLD_DELAY);
  button.setPressHandler(onResistivePress);
  button.setClickHandler(onResistiveClick);
  button.setHoldHandler(onResistiveHold);
  button.setReleaseHandler(onResistiveRelease);

  Serial.begin(230400);

  while (!Serial);
  Serial.flush();
}

void loop() {
  // check for command data
  parseIncoming();

  if (isStarted) {
    #if defined(HDRadioSerial)
    processRadioIncoming();
    #endif // if defined(HDRadioSerial)

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
      isLengthByte  = true;
      isEscaped     = false;

      checksum     = 0xF1;
      packetLength = 0;
      bufIndex     = 0;
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
        checksum    += packetLength;
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
  case MCU_START:
    processStartCommand();
    break;

  case MCU_STOP:
    processStopCommand();
    break;

  case MCU_REQUEST_ID:
    sendPacketToPc(CMD_IDENT, (byte *)mcuId, strlen(mcuId));
    break;

  case MCU_SET_DIMMER_ANALOG:
    setDimmerAnalog();
    break;

  case MCU_SET_DIMMER_DIGITAL:
    setDimmerDigital();
    break;

  case MCU_AUDIO_SOURCE_HD:
    setSourceHd();
    break;

  case MCU_AUDIO_SOURCE_AUX:
    setSourceAux();
    break;

  case MCU_RADIO_REQUEST_STATUS:
    sendPacketToPc(CMD_RADIO_STATUS, (byte *)&radioConnected, sizeof(radioConnected));
    break;

  case MCU_RADIO_SEND_PACKET:
    #if defined(HDRadioSerial)
    sendRadioPacket();
    #endif // if defined(HDRadioSerial)
    break;

  case MCU_RADIO_SET_DTR:
    #if defined(HDRadioSerial)
    setDtr((inBuffer[1] == 0x01));
    #endif // if defined(HDRadioSerial)
    break;

  case MCU_RADIO_SET_RTS:
    #if defined(HDRadioSerial)
    setRts((inBuffer[1] == 0x01));
    #endif // if defined(HDRadioSerial)
    break;

  case MCU_CUSTOM:
    processCustom();
    break;

  default: {
      // Unknown command, send it back to the device log
      // TODO: convert the hex buffer to string and send via log
      const char str[] = "Unknown Command Received";
      sendPacketToPc(CMD_LOG, (byte *)str, strlen(str));
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
    isDimmerOn            = false;
    inReverse             = false;
    reverse_start_time    = 0;
    analog_dimmer_reading = 0;
  }

  sendPacketToPc(CMD_STARTED, (byte *)mcuId, strlen(mcuId));
  isStarted = true;

  #ifdef LED_PIN
  digitalWrite(LED_PIN, HIGH);
  #endif // ifdef LED_PIN
}

void processStopCommand() {
  isStarted             = false;
  isDimmerOn            = false;
  inReverse             = false;
  reverse_start_time    = 0;
  analog_dimmer_reading = 0;

  #if defined(HDRadioSerial)
  setDtr(false);
  setRts(false);
  #endif // if defined(HDRadioSerial)

  #ifdef LED_PIN
  digitalWrite(LED_PIN, LOW);
  #endif // ifdef LED_PIN
}

void setDimmerAnalog() {
  analogDimmerEnabled   = true;
  analog_dimmer_reading = 0;
}

void setDimmerDigital() {
  analogDimmerEnabled = false;
}

void setSourceHd() {
  if (audio_input_selection != HD_RADIO) {
    digitalWrite(AUDIO_SOURCE_PIN, LOW);
    audio_input_selection = HD_RADIO;

    const char str[] = "HD_RADIO_INPUT_SET";
    sendPacketToPc(CMD_LOG, (byte *)str, strlen(str));
  }
}

void setSourceAux() {
  if (audio_input_selection != AUX) {
    digitalWrite(AUDIO_SOURCE_PIN, HIGH);
    audio_input_selection = AUX;

    const char str[] = "AUX_INPUT_SET";
    sendPacketToPc(CMD_LOG, (byte *)str, strlen(str));
  }
}

#if defined(HDRadioSerial)
void processRadioIncoming() {
  uint16_t index = 0;
  uint8_t radioBuf[256];
  
  while (HDRadioSerial.available() > 0) {
    radioBuf[index] = HDRadioSerial.read();
    index++;
    delay(2);  // Delay 2ms to wait for more incoming
  }

  if (index > 0) {
    sendPacketToPc(CMD_RADIO_DATA, radioBuf, index);
  }

}

void sendRadioPacket() {
  uint8_t *radioBuf = inBuffer + 1;     // Radio packet starts after the command
  uint16_t radioLength   = packetLength - 1; // buffer length minus command

  HDRadioSerial.write(radioBuf, radioLength);
}

void setDtr(bool status) {
  if (status) {
    // raise dtr
    if (!radioDtrOn) {
      digitalWrite(RADIO_DTR_PIN, HIGH);
      radioDtrOn = true;
    }
  } else {
    // lower dtr
    if (radioDtrOn) {
      digitalWrite(RADIO_DTR_PIN, LOW);
      radioDtrOn = false;
    }
  }
}

void setRts(bool status) {
  if (status) {
    // raise rts
    if (!radioRtsOn) {
      digitalWrite(RADIO_RTS_PIN, HIGH);
      radioRtsOn = true;
    }
  } else {
    // lower rts
    if (radioRtsOn) {
      digitalWrite(RADIO_RTS_PIN, LOW);
      radioRtsOn = false;
    }
  }
}

#endif // if defined(HDRadioSerial)

void processReverse() {
  if (digitalRead(REVERSE_PIN) == LOW) {
    if (!inReverse) {
      if (reverse_start_time == 0) {
        reverse_start_time = millis();
      } else if (millis() >= reverse_start_time + REVERSE_DELAY) {
        inReverse = true;
        sendPacketToPc(CMD_REVERSE, (byte *)&inReverse,
                       sizeof(inReverse));
      }
    } 
  } else if (inReverse){
      inReverse          = false;
      reverse_start_time = 0;
      sendPacketToPc(CMD_REVERSE, (byte *)&inReverse,
                     sizeof(inReverse));
  }
}

void processDimmer() {
  if (digitalRead(DIMMER_DIGITAL_PIN) == LOW) {
    if (!isDimmerOn) {
      isDimmerOn = true;
      sendPacketToPc(CMD_DIMMER, (byte *)&isDimmerOn,
                     sizeof(isDimmerOn));
    } else if (analogDimmerEnabled) {
      delay(BUTTON_DEBOUNCE_DELAY);  // delay after digital pin reading to eliminate noise for analog read

      // take the mean of 20 readings ( the same way done for buttons)
      for (uint8_t i = 0; i < SMOOTH; i++) {
        ave.push(analogRead(DIMMER_ANALOG_PIN));
      }
      uint16_t reading = ave.mean();
      ave.clear();

      // TODO: Add a 1-2 second delay between readings?

      if ((reading > analog_dimmer_reading + ANALOG_DIMMER_VARIANCE) ||
          (reading < analog_dimmer_reading - ANALOG_DIMMER_VARIANCE)) {
        analog_dimmer_reading = reading;
        sendPacketToPc(CMD_DIMMER_LEVEL, (byte *)&analog_dimmer_reading,
                       sizeof(analog_dimmer_reading));
      }
    }
  } else if (isDimmerOn) {
    
      isDimmerOn = false;
      analog_dimmer_reading = 0;
      sendPacketToPc(CMD_DIMMER, (byte *)&isDimmerOn,
                     sizeof(isDimmerOn));
    
  }
}

void sendPacketToPc(byte        cmd,
                    const byte *data,
                    short       data_length) {
  if (data_length > 253) {
    // TODO: packet is too big
  }

  byte length = data_length + 1;  // Add length of command

  short checksum = 0xF1 + length;

  Serial.write(0xF1);          // Start header
  writeEscapedByte(length);    // Length of command (not including escape bytes
                               // and header)
  writeEscapedByte(cmd);       // command
  checksum += cmd;


  for (size_t i = 0; i < data_length; i++) {
    writeEscapedByte(data[i]);
    checksum += data[i];
  }

  byte cksum = checksum % 256;
  writeEscapedByte(cksum);
}

// writes a byte to serial, checking to see if it should be escaped.  This uses
// 0x1A (ascii substitute) instead of 0x1B (Esc) for escaping, so as not to
// confuse with radio packets
void writeEscapedByte(byte b) {
  if (b == 0x1A) {
    // escape 0x1A as 0x1A
    Serial.write(0x1A);
    Serial.write(0x1A);
  } else if (b == 0xF1) {
    // escape 0xF1 as 0x20
    Serial.write(0x1A);
    Serial.write(0x20);
  } else {
    Serial.write(b);
  }
}



#include <Average.h>
#include <Button.h>
#include "autodefs.h"

// Set your own unique ID, it should be 8 alphanumeric digits
const char *mcuId = "TEST1234";
enum AudioInput { HD_RADIO, AUX };
AudioInput audio_input_selection = HD_RADIO;


char testcmd[20];
char testdata[30];
uint16_t  tstIdx = 0;
bool isCmd  = true;

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
uint16_t     checksum      = 0;
bool    isLengthByte  = false;
bool    isValidPacket = false;
bool    isEscaped     = false;


void setup() {
  #ifdef LED_PIN
  pinMode(LED_PIN, OUTPUT);
  digitalWrite(LED_PIN, LED_OFF);
  #endif // ifdef LED_PIN

  #if defined(HDRadioSerial)
  pinMode(RADIO_DTR_PIN,     OUTPUT);
  pinMode(RADIO_RTS_PIN,     OUTPUT);
  HDRadioSerial.begin(115200);
  HDRadioSerial.flush();
  radioConnected = true;
  #endif // if defined(HDRadioSerial)

  Serial.begin(115200);

  while (!Serial);
  Serial.flush();

  Serial1.begin(115200);

  while (!Serial1);
  Serial1.flush();
}

void loop() {
  // read serial commands from the tablet if available
  parseFromAndroid();

  if (isStarted) {
    #if defined(HDRadioSerial)
    processRadioIncoming();
    #endif // if defined(HDRadioSerial)

    parseTestInput();
  }

}
void parseTestInput() {
  // TODO:  need to rework so we use unprintable ascii chars as separators
  while (Serial1.available() > 0) {
    char ch = Serial1.read();

    if (ch == '<') {
      tstIdx = 0;
      isCmd  = true;
    } else if (ch == ':') {
      testcmd[tstIdx] = 0;
      tstIdx          = 0;
      isCmd           = false;
    } else if (ch == '>') {
      testdata[tstIdx] = 0;

      // TODO: need to get byte type

      sendTestCommand(testcmd, testdata);

      tstIdx = 0;
      isCmd  = true;
    } else {
      if (isCmd) {
        testcmd[tstIdx] = ch;
        tstIdx++;
      } else {
        testdata[tstIdx] = ch;
        tstIdx++;
      }
    }
  }
}

void parseFromAndroid() {
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
          const char str[] = "Error, packet checksum mismatch";
          sendPacketToPc(CMD_LOG, (byte *)str, strlen(str));
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
      Serial1.println("Sending Radio Packet");
      sendRadioPacket();
      #endif // if defined(HDRadioSerial)
      break;
  
    case MCU_RADIO_SET_DTR:
      #if defined(HDRadioSerial)
      Serial1.println("Setting Radio DTR");
      setDtr((inBuffer[1] == 0x01));
      #endif // if defined(HDRadioSerial)
      break;
  
    case MCU_RADIO_SET_RTS:
      #if defined(HDRadioSerial)
      Serial1.println("Setting Radio RTS");
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

void sendTestCommand(const char *command, const char *testdata) {
  if (strcmp(command, "Click") == 0) {
    uint16_t data = atoi(testdata);
    sendPacketToPc(CMD_CLICK, (byte *)&data, sizeof(data));
  } else if (strcmp(command, "Hold") == 0) {
    uint16_t data = atoi(testdata);
    sendPacketToPc(CMD_HOLD, (byte *)&data, sizeof(data));
  } else if (strcmp(command, "Release") == 0) {
    uint16_t data = atoi(testdata);
    sendPacketToPc(CMD_RELEASE, (byte *)&data, sizeof(data));
  } else if (strcmp(command, "Dimmer") == 0) {
    if (strcmp(testdata, "On") == 0) {
      bool dim = true;
      sendPacketToPc(CMD_DIMMER, (byte *)&dim, sizeof(dim));
    } else if (strcmp(testdata, "Off") == 0) {
      bool dim = false;
      sendPacketToPc(CMD_DIMMER, (byte *)&dim, sizeof(dim));
    }
  } else if (strcmp(command, "Dimmer Level") == 0) {
      uint16_t data = atoi(testdata);
      sendPacketToPc(CMD_DIMMER_LEVEL,(byte *)&data, sizeof(data));
  } else if (strcmp(command, "Reverse") == 0) {
    if (strcmp(testdata, "On") == 0) {
      bool rev = true;
      sendPacketToPc(CMD_REVERSE, (byte *)&rev, sizeof(rev));
    } else if (strcmp(testdata, "Off") == 0) {
      bool rev = false;
      sendPacketToPc(CMD_REVERSE, (byte *)&rev, sizeof(rev));
    }
  }  else if (strcmp(command, "Log") == 0) {
    sendPacketToPc(CMD_LOG, (byte *)testdata, strlen(testdata));
  } else if (strcmp(command, "Custom") == 0) {
    sendPacketToPc(CMD_CUSTOM, (byte *)testdata, strlen(testdata));
  } else {
    // TODO: unknown command

    // return 0;
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
  }

  sendPacketToPc(CMD_STARTED, (byte *)mcuId, strlen(mcuId));
  isStarted = true;
  Serial1.println("MCU Started and Connected");

  #ifdef LED_PIN
  digitalWrite(LED_PIN, LED_ON);
  #endif // ifdef LED_PIN
}

void processStopCommand() {
  isStarted             = false;
  isDimmerOn            = false;
  inReverse             = false;

  #if defined(HDRadioSerial)
  setDtr(false);
  setRts(false);
  #endif // if defined(HDRadioSerial)

  Serial1.println("MCU Stopped");
  #ifdef LED_PIN
  digitalWrite(LED_PIN, LED_OFF);
  #endif // ifdef LED_PIN
}

void setDimmerAnalog() {
  Serial1.println("Dimmer mode set to Analog");
  analogDimmerEnabled   = true;
}

void setDimmerDigital() {
  Serial1.println("Dimmer mode set to Digital");
  analogDimmerEnabled = false;
}

void setSourceHd() {
  if (audio_input_selection != HD_RADIO) {
    Serial1.println("Source set to HD Radio");
    audio_input_selection = HD_RADIO;

    const char str[] = "HD_RADIO_INPUT_SET";
    sendPacketToPc(CMD_LOG, (byte *)str, strlen(str));
  }
}

void setSourceAux() {
  if (audio_input_selection != AUX) {
    Serial1.println("Source set to AUX");
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
  //for (uint16_t i = 0; i < radioLength; i++) {
   // HDRadioSerial.write(radioBuf[i]);
    //delay(1);
  //}

  Serial1.println("Packet sent to radio:");
  Serial1.write(radioBuf, radioLength);
  Serial1.println("END");
}

void setDtr(bool status) {
  if (status) {
    // raise dtr
    if (!radioDtrOn) {
      digitalWrite(RADIO_DTR_PIN, HIGH);
      radioDtrOn = true;
      Serial1.println("Set DTR On");
    }
  } else {
    // lower dtr
    if (radioDtrOn) {
      digitalWrite(RADIO_DTR_PIN, LOW);
      radioDtrOn = false;
      Serial1.println("Set DTR Off");
    }
  }
}

void setRts(bool status) {
  if (status) {
    // raise rts
    if (!radioRtsOn) {
      digitalWrite(RADIO_RTS_PIN, HIGH);
      radioRtsOn = true;
      Serial1.println("Set RTS On");
    }
  } else {
    // lower rts
    if (radioRtsOn) {
      digitalWrite(RADIO_RTS_PIN, LOW);
      radioRtsOn = false;
      Serial1.println("Set RTS Off");
    }
  }
}



#endif // if defined(HDRadioSerial)

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

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

enum AudioInput { HD_RADIO, AUX };

Average<unsigned int> ave(SMOOTH);

ButtonCB button(BUTTON_DIGITAL_PIN, Button::PULL_UP, BUTTON_DEBOUNCE_DELAY);

AudioInput audio_input_selection = HD_RADIO;


// TODO: change shorts to ints, should be able to handle the different sizes
unsigned short btn_analog_value      = 0;
unsigned short analog_dimmer_reading = 0;

unsigned long reverse_start_time = 0;

bool isStarted           = false;
bool isHolding           = false;
bool inReverse           = false;
bool isDimmerOn          = false;
bool analogDimmerEnabled = false;

char command[30];
byte cmd_index = 0;

void onResistivePress(const Button& b) {
  for (int i = 0; i < SMOOTH; i++) {
    ave.push(analogRead(BUTTON_ANALOG_PIN));
  }
  btn_analog_value = ave.mean();
  ave.clear();
}

void onResistiveClick(const Button& b) {
  sendPacketToPc(CMD_CLICK, TYPE_SHORT, (byte *)&btn_analog_value,
                 sizeof(btn_analog_value));
}

void onResistiveHold(const Button& b) {
  isHolding = true;
  sendPacketToPc(CMD_HOLD, TYPE_SHORT, (byte *)&btn_analog_value,
                 sizeof(btn_analog_value));
}

void onResistiveRelease(const Button& b) {
  if (isHolding) {
    isHolding = false;
    sendPacketToPc(CMD_RELEASE, TYPE_SHORT, (byte *)&btn_analog_value,
                   sizeof(btn_analog_value));
  }
}

void setup() {
  Serial.begin(9600);

  while (!Serial);
  Serial.flush();

  pinMode(BUTTON_ANALOG_PIN, INPUT_ANALOG);
  pinMode(DIMMER_ANALOG_PIN, INPUT_ANALOG);
  pinMode(AUDIO_SOURCE_PIN,  OUTPUT);

  #ifdef LED_PIN
  pinMode(PB1,               OUTPUT);
  #endif // ifdef LED_PIN

  button.setHoldThreshold(BUTTON_HOLD_DELAY);
  button.pressHandler(onResistivePress);
  button.clickHandler(onResistiveClick);
  button.holdHandler(onResistiveHold);
  button.releaseHandler(onResistiveRelease);

  delay(500);
}

void loop() {
  // check for command data
  if (Serial.available() > 0) {
    char c = Serial.read();

    if (c == '<') {
      cmd_index = 0;
    } else if (c == '>') {
      if (cmd_index < 29) {
        command[cmd_index] = 0;
        executeCmd();
      } else {
        // TODO: send a log message that the incoming command is too big
      }
    } else if (cmd_index < 30) {
      command[cmd_index] = c;
      cmd_index++;
    }
  }

  if (isStarted) {
    // check for reverse
    processReverse();

    // check dimmer
    processDimmer();

    // check for button press
    button.process();
  }
}

// TODO: For AVR architecture I should probably use strcmp_P and PSTR() for all
// of the constants
void executeCmd() {
  if (strcmp(command, "START") == 0) {
    delay(1000); // delay one sec so the app is ready
                 // to receive

    if (isStarted) {
      // reset variables so commands can be resent if necessary
      isDimmerOn            = false;
      inReverse             = false;
      reverse_start_time    = 0;
      analog_dimmer_reading = 0;
    }

    const char *str = "SUCCESS";
    sendPacketToPc(CMD_CONNECTED, TYPE_STRING, (byte *)str, strlen(str));
    isStarted = true;

    #ifdef LED_PIN
    digitalWrite(LED_PIN, HIGH);
    #endif // ifdef LED_PIN
  } else if (strcmp(command, "STOP") == 0) {
    isStarted             = false;
    isDimmerOn            = false;
    inReverse             = false;
    reverse_start_time    = 0;
    analog_dimmer_reading = 0;

    #ifdef LED_PIN
    digitalWrite(LED_PIN, LOW);
    #endif // ifdef LED_PIN
  } else if (strcmp(command, "Dimmer:Analog") == 0) {
    analogDimmerEnabled   = true;
    analog_dimmer_reading = 0;
  } else if (strcmp(command, "Dimmer:Digital") == 0) {
    analogDimmerEnabled = false;
  } else if (strcmp(command, "Source:HD_RADIO") == 0) {
    if (audio_input_selection != HD_RADIO) {
      digitalWrite(AUDIO_SOURCE_PIN, LOW);
      audio_input_selection = HD_RADIO;

      const char str[] = "HD_RADIO_INPUT_SET";
      sendPacketToPc(CMD_LOG, TYPE_STRING, (byte *)str, strlen(str));
    }
  } else if (strcmp(command, "Source:AUX") == 0) {
    if (audio_input_selection != AUX) {
      digitalWrite(AUDIO_SOURCE_PIN, HIGH);
      audio_input_selection = AUX;

      const char str[] = "AUX_INPUT_SET";
      sendPacketToPc(CMD_LOG, TYPE_STRING, (byte *)str, strlen(str));
    }
  } else if (command[0] != 0) {
    // Unknown command, send it back to the device log
    char logstring[40] = "Unknown:";
    strcat(logstring, command);
    sendPacketToPc(CMD_LOG, TYPE_STRING, (byte *)logstring, strlen(logstring));
  }
}

void processReverse() {
  if (digitalRead(REVERSE_PIN) == HIGH) {
    if (!inReverse) {
      if (reverse_start_time == 0) {
        reverse_start_time = millis();
      } else if (millis() >= reverse_start_time + REVERSE_DELAY) {
        inReverse = true;
        sendPacketToPc(CMD_REVERSE, TYPE_BOOLEAN, (byte *)&inReverse,
                       sizeof(inReverse));
      }
    } else {
      inReverse          = false;
      reverse_start_time = 0;
      sendPacketToPc(CMD_REVERSE, TYPE_BOOLEAN, (byte *)&inReverse,
                     sizeof(inReverse));
    }
  }
}

void processDimmer() {
  if (digitalRead(DIMMER_DIGITAL_PIN) == HIGH) {
    if (!isDimmerOn) {
      isDimmerOn = true;
      sendPacketToPc(CMD_DIMMER, TYPE_BOOLEAN, (byte *)&isDimmerOn,
                     sizeof(isDimmerOn));
    } else {
      // Analog read here, but only if analog is enabled
      if (analogDimmerEnabled) {
        unsigned int reading = analogRead(DIMMER_ANALOG_PIN);

        if ((reading > analog_dimmer_reading + ANALOG_DIMMER_VARIANCE) ||
            (reading < analog_dimmer_reading - ANALOG_DIMMER_VARIANCE)) {
          analog_dimmer_reading = reading;
          sendPacketToPc(CMD_DIMMER, TYPE_SHORT, (byte *)&analog_dimmer_reading,
                         sizeof(analog_dimmer_reading));
        }
      }
    }
  } else {
    if (isDimmerOn) {
      isDimmerOn = false;
      sendPacketToPc(CMD_DIMMER, TYPE_BOOLEAN, (byte *)&isDimmerOn,
                     sizeof(isDimmerOn));
    }
  }
}

void sendPacketToPc(byte        cmd,
                    byte        data_type,
                    const byte *data,
                    short       data_length) {
  if (data_length > 253) {
    // TODO: packet is too big
  }

  byte length = data_length + 2;

  short checksum = length;

  Serial.write(0xF1);   // Start header
  writeByte(length);    // Length of command (not including escape bytes and
                        // header)
  writeByte(cmd);       // command
  checksum += cmd;

  writeByte(data_type); // data type
  checksum += data_type;

  for (size_t i = 0; i < data_length; i++) {
    writeByte(data[i]);
    checksum += data[i];
  }

  byte cksum = checksum % 256;
  writeByte(cksum);
}

// writes a byte to serial, checking to see if it should be escaped
void writeByte(byte b) {
  if (b == 0x1B) {
    // escape 0x1B as 0x1B
    Serial.write(0x1B);
    Serial.write(0x1B);
  } else if (b == 0xF1) {
    // escape 0xF1 as 0x20
    Serial.write(0x1B);
    Serial.write(0x20);
  } else {
    Serial.write(b);
  }
}

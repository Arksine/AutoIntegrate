#ifndef DEFINITIONS_H
#define DEFINITIONS_H

// TODO: There should already be flags defined that I can use to easily
// determine the architecture
#define ARCH_32BIT

#define SMOOTH 20
#define REVERSE_DELAY 500
#define BUTTON_HOLD_DELAY 800
#define BUTTON_DEBOUNCE_DELAY 22

// this definition is the variance between analog readings necessary to transmit
// a new reading it should be much lower for 10-bit ADCs.
#define ANALOG_DIMMER_VARIANCE 100

#define LED_PIN             PB1
#define BUTTON_ANALOG_PIN   PA0
#define BUTTON_DIGITAL_PIN  PA11
#define DIMMER_ANALOG_PIN   PA1
#define DIMMER_DIGITAL_PIN  PA12
#define REVERSE_PIN         PA15
#define AUDIO_SOURCE_PIN    PB6

// Data Type definitions
#define TYPE_SHORT 0x01
#define TYPE_INT 0x02
#define TYPE_STRING 0x03
#define TYPE_BOOLEAN 0x04
#define TYPE_TUNEINFO 0x05
#define TYPE_HDSONGINFO 0x06

// Command Definitions
#define CMD_CONNECTED 0x01
#define CMD_CLICK 0x02
#define CMD_HOLD 0x03
#define CMD_RELEASE 0x04
#define CMD_DIMMER 0x05
#define CMD_REVERSE 0x06
#define CMD_RADIO 0x07
#define CMD_LOG 0x08
#define CMD_CUSTOM 0x09


#endif // ifndef DEFINITIONS_H

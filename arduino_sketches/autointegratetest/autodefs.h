#ifndef AUTODEFS_H
#define AUTODEFS_H

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

#define LED_PIN             PB1             // LED Pin for Maple Mini
#define RADIO_DTR_PIN       PB7
#define RADIO_RTS_PIN       PB6

// Set this to the serial port interfacing with the radio, or comment out if you
// do
// not want to use radio control
#define HDRadioSerial Serial3

// Outgoing Command Definitions (sent to host)
#define CMD_STARTED 0x01
#define CMD_IDENT 0x02
#define CMD_CLICK 0x03
#define CMD_HOLD 0x04
#define CMD_RELEASE 0x05
#define CMD_DIMMER 0x06
#define CMD_DIMMER_LEVEL 0x07
#define CMD_REVERSE 0x08
#define CMD_RADIO_STATUS 0x09
#define CMD_RADIO_DATA 0x0A
#define CMD_LOG 0x0B
#define CMD_CUSTOM 0x0C

// MCU Command Definitions (received from host)
#define MCU_START 0x01
#define MCU_STOP 0x02
#define MCU_REQUEST_ID 0x03
#define MCU_SET_DIMMER_ANALOG 0x04
#define MCU_SET_DIMMER_DIGITAL 0x05
#define MCU_AUDIO_SOURCE_HD 0x06
#define MCU_AUDIO_SOURCE_AUX 0x07
#define MCU_RADIO_REQUEST_STATUS 0x08
#define MCU_RADIO_SEND_PACKET 0x09
#define MCU_RADIO_SET_DTR 0x0A
#define MCU_RADIO_SET_RTS 0x0B
#define MCU_CUSTOM 0x0C


// Enter custom commands here, do not use 0x1A or 0xF1 as custom command values

#endif // ifndef DEFINITIONS_H

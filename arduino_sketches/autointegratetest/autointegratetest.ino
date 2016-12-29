
// TODO: There should already be flags defined that I can use to easily
// determine the architecture
#define ARCH_32BIT

// #define ARCH_8BIT

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


char testcmd[20];
char testdata[30];
int  tstIdx = 0;
bool isCmd  = true;

char command[30];
int  cmdIdx = 0;


void setup() {
  pinMode(PB1, OUTPUT);

  Serial.begin(9600);

  while (!Serial);
  Serial.flush();

  Serial1.begin(9600);

  while (!Serial1);
  Serial1.flush();
}

void loop() {
  // read serial commands from the tablet if available

  if (Serial.available() > 0) {
    char c = Serial.read();

    if (c == '<') {
      cmdIdx = 0;
    } else if (c == '>') {
      command[cmdIdx] = 0;
      executeCmd();
    } else {
      command[cmdIdx] = c;
      cmdIdx++;
    }
  }

  // TODO:  need to rework so we use unprintable ascii chars as separators
  if (Serial1.available() > 0) {
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

  delay(2);
}

void executeCmd() {
  if (strcmp(command, "START") == 0) {
    delay(1000); // delay one sec so the app is ready
                 // to receive

    const char *success = "SUCCESS";
    sendPacketToPc(CMD_LOG, TYPE_STRING, (byte *)success, 7);

    Serial1.println(F("<START:SUCCESS>"));

    digitalWrite(PB1, HIGH);

    delay(2);
  } else if (strcmp(command, "STOP") == 0) {
    digitalWrite(PB1, LOW);
    Serial1.println(F("<STOP:SUCCESS>"));
  } else {
    Serial1.print(F("<Unknown Command:"));
    Serial1.print(command);
    Serial1.println(">");
  }
}

void sendTestCommand(const char *command, const char *testdata) {
  if (strcmp(command, "Connected") == 0) {
    sendPacketToPc(CMD_CONNECTED, TYPE_STRING, (byte *)testdata,
                   strlen(testdata));
  } else if (strcmp(command, "Click") == 0) {
    int data = atoi(testdata);
    sendPacketToPc(CMD_CLICK, TYPE_INT, (byte *)&data, sizeof(data));
  } else if (strcmp(command, "Hold") == 0) {
    int data = atoi(testdata);
    sendPacketToPc(CMD_HOLD, TYPE_INT, (byte *)&data, sizeof(data));
  } else if (strcmp(command, "Release") == 0) {
    int data = atoi(testdata);
    sendPacketToPc(CMD_RELEASE, TYPE_INT, (byte *)&data, sizeof(data));
  } else if (strcmp(command, "Dimmer") == 0) {
    if (strcmp(testdata, "On") == 0) {
      bool dim = true;
      sendPacketToPc(CMD_DIMMER, TYPE_BOOLEAN, (byte *)&dim, sizeof(dim));
    } else if (strcmp(testdata, "Off") == 0) {
      bool dim = false;
      sendPacketToPc(CMD_DIMMER, TYPE_BOOLEAN, (byte *)&dim, sizeof(dim));
    } else {
      int data = atoi(testdata);
      sendPacketToPc(CMD_DIMMER, TYPE_INT, (byte *)&data, sizeof(data));
    }
  } else if (strcmp(command, "Reverse") == 0) {
    if (strcmp(testdata, "On") == 0) {
      bool rev = true;
      sendPacketToPc(CMD_REVERSE, TYPE_BOOLEAN, (byte *)&rev, sizeof(rev));
    } else if (strcmp(testdata, "Off") == 0) {
      bool rev = false;
      sendPacketToPc(CMD_REVERSE, TYPE_BOOLEAN, (byte *)&rev, sizeof(rev));
    }
  } else if (strcmp(command, "Radio") == 0) {
    // TODO: parse testdata with simulated radio packet, send to PC
    // return RADIO;
  } else if (strcmp(command, "Log") == 0) {
    sendPacketToPc(CMD_LOG, TYPE_STRING, (byte *)testdata, strlen(testdata));
  } else if (strcmp(command, "Custom") == 0) {
    sendPacketToPc(CMD_CUSTOM, TYPE_STRING, (byte *)testdata, strlen(testdata));
  } else {
    // TODO: unknown command

    // return 0;
  }
}

void sendPacketToPc(byte        command,
                    byte        data_type,
                    const byte *data,
                    int         data_length) {
  byte length   = data_length + 2;
  int  checksum = length;

  Serial.write(0xF1);   // Start header
  writeByte(length);    // Length of command (not including escape bytes and
                        // header)
  writeByte(command);   // command
  checksum += command;

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

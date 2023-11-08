# nesemu

A NES emulator written from scratch in Java, with basic netplay support.

![The emulator running Spacegulls](/screenshots/spacegulls.png?raw=true)

_Game:_ [Spacegulls](https://morphcatgames.itch.io/spacegulls)

Implemented iNES mappers: [000](https://www.nesdev.org/wiki/INES_Mapper_000), [001](https://www.nesdev.org/wiki/INES_Mapper_001), [002](https://www.nesdev.org/wiki/INES_Mapper_002), [003](https://www.nesdev.org/wiki/INES_Mapper_003).

## Installation and building

Build the project from its root directory using [ant](https://ant.apache.org/):

```
$ ant
```

The resulting `.jar` will be stored in the `build/` directory.

**Note 1:** the project has been developed and tested with JDK 8, but its compatibility with older JDK versions has not been thoroughly verified.

**Note 2:** [FlatLaf](https://github.com/JFormDesigner/FlatLaf) is used for the Swing GUI look and feel; its `.jar` is already included in the `lib/` directory.

## Usage

### Using the emulator

- **Loading cartridges/ROMs**: _System -> Load ROM_
- **Reset the console**: _System -> Reset_
- **Exit the emulator**: _System -> Exit_

The correspondence between NES buttons and emulator keys is the following:

| NES button      | Key                       |
| --------------- | ------------------------- |
| Directional pad | Up/Down/Left/Right arrows |
| A               | X                         |
| B               | Z                         |
| Start           | Enter                     |
| Select          | Shift                     |

### Netplay: online multiplayer

In any netplay session, there will be two players present: one will be the **server** and the other will be the **client**. The server will be assigned controller 1 and will pick the played ROM; the client will be assigned controller 2.

#### For the server

Select _Netplay -> Local server -> Start_ and wait for the client to connect.

#### For the client

Select _Netplay -> Connect to server_ and input the server's hostname and port.

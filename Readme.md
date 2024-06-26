# Server-side support DH fork 1.21

**Official repo got updated download from there: [Download](https://gitlab.com/s809/minecraft-lod-mod/-/pipelines/1349508836/builds)**

This is a work-in-progress, don't expect it to work correctly! Especially 1.21

"Open to LAN" servers don't work yet, use a dedicated server instead.

## Links
- Downloads: [1.16.5](https://gitlab.com/s809/minecraft-lod-mod/-/jobs/artifacts/main/download?job=build:%20%5B1.16.5%5D), [1.17.1](https://gitlab.com/s809/minecraft-lod-mod/-/jobs/artifacts/main/download?job=build:%20%5B1.17.1%5D), [1.18.2](https://gitlab.com/s809/minecraft-lod-mod/-/jobs/artifacts/main/download?job=build:%20%5B1.18.2%5D), [1.19.2](https://gitlab.com/s809/minecraft-lod-mod/-/jobs/artifacts/main/download?job=build:%20%5B1.19.2%5D), [1.19.4](https://gitlab.com/s809/minecraft-lod-mod/-/jobs/artifacts/main/download?job=build:%20%5B1.19.4%5D), [1.20.1](https://gitlab.com/s809/minecraft-lod-mod/-/jobs/artifacts/main/download?job=build:%20%5B1.20.1%5D), [1.20.2](https://gitlab.com/s809/minecraft-lod-mod/-/jobs/artifacts/main/download?job=build:%20%5B1.20.2%5D), [1.20.4](https://gitlab.com/s809/minecraft-lod-mod/-/jobs/artifacts/main/download?job=build:%20%5B1.20.4%5D), [1.21](https://github.com/Zaarrg/dh-server/releases)
- **1.21 is only for Fabric.**
- [Build status](https://gitlab.com/s809/minecraft-lod-mod/-/pipelines/latest)
- [Support thread](https://discord.com/channels/881614130614767666/1154490009735417989) (Read the pins before talking!)
- Server invite link: https://discord.gg/xAB8G4cENx


## How to install
1. Drop the matching jar into `mods/` of the server and clients.
2. On server: open an additional TCP port. The process is same as for opening MC port. Default port is 25049, but you can change it in mod's settings & config.

## FAQ
- How to open ports?
    - [How to open Minecraft port](<https://www.google.com/search?q=how+to+open+minecraft+port>). However, this is not necessary if you don't expose MC server to the internet or use port forwarding tools like ngrok/playit.
- My port forwarding tool gave me wrong port/two different IPs!
    - Change connect config using these commands:
        - `/dhconfig connectIpOverride <given IP>`,
        - `/dhconfig connectPortOverride <given port>`,
    - then rejoin the server.
- How to check if fork is installed correctly?
    - Check for connection status F3 line when on server, in between other DH status lines.
- Will players with mainline DH/without the mod at all be able to connect to the server with DH fork?
    - Yes, everything will function normally but they won't receive LODs from server.
- Does/Will this fork support X (not related to networking/server side)?
    - It supports X the same way mainline DH supports it.
- How to change server side settings?
    - Use `/dhconfig` (operator only)
- How to import pre-generated LODs?
    - Put them into `world/`, in the structure same as of singleplayer world (more info below).

## LOD data layout

**Singleplayer:**
- Overworld: `.minecraft/saves/WORLD_NAME/data/DistantHorizons.sqlite`
- Nether: `.minecraft/saves/WORLD_NAME/DIM-1/data/DistantHorizons.sqlite`
- End: `.minecraft/saves/WORLD_NAME/DIM1/data/DistantHorizons.sqlite`
- Custom Dimensions: `.minecraft/saves/WORLD_NAME/DIMENSION_FOLDER/data/DistantHorizons.sqlite`

**Multiplayer:**
- `.minecraft/Distant_Horizons_server_data/SERVER_NAME/`

**Dedicated servers**
- Same as singleplayer, except `.minecraft/saves/WORLD_NAME/` should be replaced with `server_folder/world/`.

<p align="center">
  <img src="docs/orion/Orion-TV_v1.jpg" alt="Orion TV. Zero ads, lightening fast TV. Debloat and transform any Android or Google TV over Wi-Fi. No root, fully reversible." height="300"></a>
</p>

## Orion TV: an Apple TV-inspired theme

<p align="center"><img src="docs/orion/home-02.jpg" alt="Orion on a Sony BRAVIA: a thin clock and the date over Mount Shasta at dusk, a row of ten frosted app tiles below with white wordmark logos, and four glass buttons in the top right corner."></p>

One TV's home screen, rebuilt over a few evenings on top of CleanYourTV's launcher: ten near-square frosted tiles
over a blue-hour picture, each tile the picture behind it softened, with the app's own colour rising from its bottom
edge. The clock, the tiles and the buttons take the same light, so the row sits in the landscape instead of on top of
it. Nothing here is upstream's default: every switch behind it is off unless a theme turns it on, and the launcher
still has no network permission, no analytics and no background services.

| Stock Google TV home | Orion TV |
|---|---|
| ![The stock home screen: a YouTube hero row over sponsored rows](docs/orion/before-stock-google-tv.jpg) | ![The same TV with the Orion theme: one row of frosted tiles over a mountain at dusk](docs/orion/home-02.jpg) |
| *Sponsored rows and an autoplaying hero* | *One row of glass dock, sleek and minimalist!* |

| | |
|---|---|
| ![Home over a storm-lit range, HBO Max focused](docs/orion/home-01.jpg) | ![The screensaver over the same range, clock only](docs/orion/screensaver-05.jpg) |
| *The focused tile lifts, takes the app's colour at its bottom and names itself underneath* | *Idle for ten minutes: the screensaver is Home without the tiles* |
| ![A snow face in midday sun above the tile row](docs/orion/home-03.jpg) | ![The screensaver over the same snow face](docs/orion/screensaver-03.jpg) |
| *Every tile is the wallpaper behind it, frosted, never a flat plate* | *Same clock, same picture: nothing to burn in, and it rotates with Home* |
| ![Home over a dark valley with the windows of a chalet lit](docs/orion/home-04.jpg) | ![The screensaver over the same valley](docs/orion/screensaver-04.jpg) |
| *At night the glass stays the picture's own light* | *The screensaver keeps the rotation going through all 22 pictures* |
| ![The drawer: date and time, Next wallpaper and a settings gear, four tiles, notifications](docs/orion/drawer.jpg) | ![The Boost panel: memory and storage bars, Clean up, Storage settings](docs/orion/boost.jpg) |
| *The bell opens the drawer: notifications, Next wallpaper, Hide, TV settings, screensaver, network, Bluetooth, launcher* | *Boost: memory and storage, and a Clean up that force-stops what you opened* |
| ![A tile's menu: Replace with another app, Move, App info](docs/orion/tile-menu.jpg) | ![Move mode: the carried tile with its name, the others dimmed to 45 %](docs/orion/toast.jpg) |
| *Hold OK on a tile: replace it, move it, or open its info* | *Move: arrows carry it, the others dim their logos but keep their glass, Back puts it down* |

### What is in it

- **Frosted tiles** (`frost_tiles`, `square_tiles`, `tile_accent`): each tile draws the wallpaper under it, softened
  and lightened, and the app's colour washes up through its lower half. Ten show; the rest scroll in from the right.
- **White wordmarks** (`custom_icons`, `white_logos`): one white mark per app, cut to its own outline, 78 % of the
  tile's width, so "Stremio" and "Disney+" read like the samples that started it.
- **Glass buttons** (`glass_buttons`): the bell, Wi-Fi, settings gear and Boost take the tiles' glass instead of a
  faint white pill, and fade to solid white on focus.
- **The clock** (`clock_large`): Outfit ExtraLight at 108sp, squeezed to three quarters of its width, the date under
  it, top buttons level with its cap height. The weather line under the date is the Weather add-on, with a pill that
  speaks only when there is advice for the next twelve hours.
- **The screensaver is Home** (`screensaver_home`): the same clock in the same place over the same picture, without
  the tiles, at full brightness, rotating with Home's own rotation. Nothing moves across the screen.
- **A drawer behind the bell** (`notifications_button`, `settings_key_panel`): the notifications you can read, open or
  clear, Next wallpaper, Hide this wallpaper, TV settings, the screensaver, network, Bluetooth and launcher settings.
  The remote's gear opens the same panel.
- **22 wallpapers** (`themes/aerial-glass/wallpapers`): cold blue-hour landscapes from Wikimedia Commons, credited in
  [`CREDITS.md`](themes/aerial-glass/CREDITS.md), cropped to 1920x1080 and graded a little cooler and darker. They
  shuffle every five minutes; Hide puts one away for good.
- **Boost** (`boost_button`): free memory and storage, and a Clean up that force-stops the apps you actually opened
  this session through TV Settings, then comes back home.
- **Glass toasts**, a soft cross-fade on every focus move (`soft_motion`), and the remote's own app buttons
  (`app_keys`): Netflix and Crunchyroll open Stremio, Prime Video opens Kodi, YouTube opens SmartTube, YouTube Music
  opens TV Bro.

### How to use it

1. Build and install the launcher of this repo: `TV_HOST=<tv ip> launcher/build-and-install.sh`, or on Windows
   `launcher\build-and-install.ps1 -TvHost <tv ip>`. Re-pass `-HomeKeyService -RestoreKeyServices` so the remote's
   gear reaches the drawer (`HOME_KEY_SERVICE=1` on the shell script).
2. Push the theme: `TV_HOST=<tv ip> tools/theme.sh themes/aerial-glass`, or
   `tools\theme.ps1 -TvHost <tv ip> themes\aerial-glass -Theme themes\aerial-glass` on Windows.
3. The white wordmarks are made on your own computer from a fan-made icon pack that carries no licence, so they are
   not in this repo: `tools/make_icons.ps1` then `tools/push_icons.ps1` (Windows, needs the Android SDK's `aapt2`).
   Without them the launcher draws its own white marks.
4. The weather line needs the [Weather add-on](addons/weather); with no add-on the clock simply has no weather.

These are local customisations of CleanYourTV's launcher and theme format, not something its maintainers ship. The
launcher, the tools and the theme system are Gathin's, MIT-licensed; the original project is
[Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv).

---

## How it works

Android TVs have the same debug bridge as Android phones. Switch on USB debugging in the TV's
developer options, allow your computer once, and the rest is standard Android tooling:

| Step | What happens |
|---|---|
| **Back&nbsp;up** | The list of installed packages and settings. |
| **Disable** | The manufacturer's telemetry, ad services, content rows and preloaded junk, with `pm disable-user`. Nothing is uninstalled, so a restore script can undo every step. |
| **Install** | A small native launcher, made the default home screen. |

**No root, no cable, nothing uninstalled, and a one-line restore if you change your mind.**

The bloat differs by brand, so the prompt has your coding assistant discover it on your TV and ask
before touching anything you might use.

## Quick start

| You have | Do this |
|---|---|
| **A computer** | Use the prompt with a coding assistant: the three steps below. |

With a computer, you need it on the same Wi-Fi as the TV and a coding assistant with shell access.

Any of these work: [Claude Code](https://claude.com/product/claude-code), [Codex](https://github.com/openai/codex) or [Antigravity CLI](https://antigravity.google/product/antigravity-cli).

| Where | What to do |
|---|---|
| **1. On the TV** | Settings → Device Preferences → About → press OK on **Build** seven times → back → Developer options → **USB debugging** on. If you see a Network or ADB debugging toggle, turn that on too. Keep the TV on. |
| **2. On your computer** | Clone the repo and open that folder in your assistant (`git clone https://github.com/orion1911/orion-tv`), then paste [`PROMPT.md`](PROMPT.md) and fill in your TV model. |
| **3. Follow along** | Accept "Allow USB debugging?" on the TV, then answer the checklist: which apps you use, the dock size and app order, whether to keep the voice assistant, Chromecast and updates. |

Cloning matters: the assistant reuses this repo's `tools/disable.sh` and `launcher/build-and-install.sh`
instead of writing its own, and that is where the backup, the Home guard and the restore record live.

The assistant backs up, debloats, builds and installs the launcher, and checks each step with
screenshots pulled from the TV. With this repo as context it takes about fifteen minutes.

## Supported TVs

Anything running **Android TV** or **Google TV**.

| Platform | What works |
|---|---|
| **Android TV, Google TV** | The debloat and the launcher. |
| **Fire TV** | The debloat half only, since Fire OS will not hand Home to a third-party launcher. And on the 2015 Fire TV Box (Fire OS 5) even that half fails: every `pm` route is refused. |
| **A few China-market TCL sets** | They refuse to install any launcher at all; their lists say so in the header. |
| **Samsung (Tizen), LG (webOS)** | Different systems. See CleanYourTv's original [SHOWCASE.md](SHOWCASE.md#other-platforms-fire-tv-and-lg-webos) for three webOS ports, and [docs/devices/samsung-tizen.md](docs/devices/samsung-tizen.md) for what a 2021 Samsung set answers over Wi-Fi (no ADB, so no `pm disable-user`) if you are weighing a Tizen port. |

Tested lists from contributors, one folder per model in [`examples/`](examples/):

<!-- tv-brands:start: generated by tools/gen_tables.py from examples/*/tv.json; edit those, not this -->

**TCL** 69 · **Sony** 38 · **Xiaomi** 31 · **Philips** 17 · **Hisense** 11 · **Skyworth** 11 · **Amazon Fire TV** 7 ·
**BenQ** 4 · **JVC** 4 · **OnePlus** 4 · **Panasonic** 4 · **Thomson** 4 · **Google** 3 · **Haier** 3 ·
**NVIDIA Shield** 3 · **RCA** 3 · **SHARP** 3 · **Vu** 3 · **CHiQ** 2 · **EKO** 2 · **FFalcon** 2 · **Hitachi** 2 ·
**Insignia Fire TV** 2 · **Kalley** 2 · **Kogan** 2 · **Motorola** 2 · **Nokia** 2 · **onn** 2 · **Redmi** 2 ·
**RockTek** 2 · **Sankey** 2 · **Toshiba** 2 · **Vidda** 2 ·
Acer · AiPro2K · AIWA · AQUA · Artel · ASUS · Beko · BGH · Caxium · Cecotec · Challenger · Compac · Dcolor · Dialog ·
Freebox · Grundig · INCLO · Karbonn · KTC · Liberton · Lumio · Master-G · MTC · Next · Noblex · OK. · Panavox · PEAQ ·
Polytron · PRISM+ · SANYO · Sber · Seleco · Super General · Síragon · Tele System · TiVo · Tornado · Turkcell · Tuvio ·
Verizon · Vision ·
Alwtniet, Anker Nebula, BYINTEK, Epson, Formovie, Gadnic, HOTACK, HY320, JMGO, Magcubic, Portronics, Stellamore, Wanbo and XGIMI projectors

<!-- tv-brands:end -->

<details>
<summary><strong>The full list</strong></summary>

<!-- tv-table:start: generated by tools/gen_tables.py from examples/*/tv.json; edit those, not this -->

| TV | OS |
|---|---|
| Acer AR55AR2851UDPRO 55" (SCBC R4, Realtek rtd2851m), India | Android TV 14 |
| AiPro2K (Expressluck, dongnae; Changhong AI PONT family) | Android TV 14 |
| AIWA AI PONT SA (Changhong, osaka, ISDB86AW) | Google TV 14 |
| Alwtniet HY320 Mini projector (device adt3, Askey; Allwinner/HTC firmware), 1 GB, 720p | Android 11 |
| Amazon Fire TV 4K smart TV, 50" and 75" Omni Series (AFTTIFF43, brandenburg) | Fire OS 7.7.1.4 / 7.7.1.7, Android 9 |
| Amazon Fire TV smart TV (AFTEU014, greta, MediaTek m7322), Germany | Fire OS 7.6.8.5 / Android 9 |
| Amazon Fire TV Stick 4K Max, 1st gen 2021 (AFTKA, kara), Colombia | Fire OS 7.7.1.3 / Android 9 |
| Amazon Fire TV Stick 4K, 1st gen 2018 (AFTMM, mantis) | Fire OS 6.7.1.1 / Android 7.1 |
| Amazon Fire TV Stick 4K, 2nd gen 2023 (AFTKM, karat), Brazil | Fire OS 8.1.8.5 / Android 11 |
| Amazon Fire TV Stick Lite, 1st gen 2020 (AFTSS, sheldon) | Fire OS 7.7.1.7 / Android 9 |
| Amazon Fire TV, AFTDEC012E (ginza), Android 11 | Fire OS / Android 11 |
| Anker Nebula Cosmos projector D2140 (D2140-Cosmos, SDMC/Amlogic, 2 GB), Vietnam | Android TV 9 |
| Anker Nebula Mars 3 Air projector D2325 (Nebula_Mars_2_V2, taihang, MediaTek m7332), Portugal | Google TV 14 |
| Anker Nebula projector D2426 (D2426_CapsuleIIIPro, baekdu, SDMC/MediaTek m7632, 1.7 GB), Brazil | Android TV 14 |
| AQUA MatrixTV EE 50" 4K (Haier, AQ9676EU, omonia, MediaTek), Indonesia | Google TV 14 |
| Artel smart TV (CVTE TP.MT5510S.PB782, cvt_mt5863_eu, SMART_TV), Uzbekistan | Android 7.0 |
| ASUS M1G Google TV (MOKA, dahu, Realtek RTD2851F), US | Android TV 11 |
| Beko B43B880B (Arçelik, sinchon, MediaTek), Turkey | Android TV 11 |
| BenQ E55-735 (Changhong AI PONT, DVB96BQ) | Google TV 14 |
| BenQ E55-750 (Changhong AI PONT, DVB96BQ) | Google TV (Android 11) |
| BenQ GV32 portable projector (SDMC, BenQ_GV01, himalaya, MediaTek m7332) | Google TV 14 |
| BenQ QS01 Android TV dongle for BenQ projectors (Inspur, Amlogic S905Y2, 2 GB) | Android TV 12 |
| BGH Smart TV (reported model; UKNV.260821.001) | Android TV 14 |
| BYINTEK Love U4 projector, 1080P (Skyworth RG_HS352, HiSilicon Hi3751V350, 1 GB), Brazil | Android 9 |
| BYINTEK T5 Max projector (built-in Google TV stick: SkyworthDigital YYT, Amlogic), Brazil | Google TV 14 |
| Caxium 58" 147 cm UHD Smart TV (Realtek RTD285O, RTK2851P, TopTech + Zeasn), Colombia | Android TV 9 |
| Cecotec VQU10065S 65" QLED (Vestel-built, shandao, MediaTek), Spain | Android TV 14 |
| Challenger 32T22 BT (cvt_mt5863_col, board TP.MT5510.PB781, MediaTek MT5659, 1 GB RAM), Colombia | Android 7.0 |
| CHiQ AI PONT (Changhong GTVU65, dangni, MediaTek mt5867) | Google TV 14 |
| CHiQ AI PONT (DVB93CQ) | Android TV 11 |
| Compac ATV R2 (SCBC R2, Realtek RTD285O) | Android TV 9 |
| Dcolor GD2 (YHT) | Google TV 14 (SDK 34) |
| Dialog ViU (SEI Robotics SEI103DL, Amlogic, NES middleware), Sri Lanka | Android TV 9 |
| EKO 2K Google TV (MTC-built, tennoji, MediaTek mt5867 / T56) | Google TV 14 |
| EKO 4K Android TV (bangbae, MTC, MediaTek) | Android TV 11 |
| Epson LS800 laser projector (Askey HA90 module, sti6202d101, Amlogic) | Android TV 14 |
| FFalcon (雷鸟) r34a0, China (V8-A920T01-LF1V147, Amlogic T920), 1 GB | Android 5.1.1 |
| FFalcon (雷鸟) 鹤6 PRO 65S585C Pro (China, tcl_mt5879_cn, MediaTek MT9653) | Android TV 11 |
| Formovie / Fengmi projector MiProjM05 (eva, Amlogic) | Android TV 9 |
| Formovie Laser 4K projector (hanlla, MediaTek T31 / m7632, 1.8 GB) | Android TV 14 |
| Freebox Player POP (Free, fbx8am, Amlogic), IPTV/DVB-T box | Android TV 10 |
| Gadnic PROJ100W projector (built-in Google TV stick: SkyworthDigital YYT, Amlogic HP46B), Argentina | Google TV 14 |
| Google Chromecast with Google TV 4K (sabrina) | Google TV 14 |
| Google Chromecast with Google TV HD (boreal) | Google TV 14 |
| Google TV Streamer 4K (kirkwood, GZRNL) | Google TV 14 |
| Grundig Android UHD TV (Arçelik, GxGrNM, kunyang, MediaTek), Turkey | Android TV 14 |
| Haier Android TV DVB2K (broadway, HaierATVT56DVB) | Android TV 11 |
| Haier H32S80EFX (MatrixTV CE, HRT56EU, deto, MediaTek), Thailand | Google TV 14 |
| Haier MatrixTV GE (HR9216EU, haeundae, MediaTek), EU | Google TV 14 |
| Hisense 55QD6QF Fire TV (AFTOLCBC82, ginza), Canada | Fire OS 8.1.8.0 / Android 11 |
| Hisense 65D62QH (VIDAA_TV, MediaTek MT9653), China | Android TV 11 |
| Hisense E5Q Pro (VIDAA_TV, MediaTek MT9653), China | Android TV 11 |
| Hisense Fire TV (AFTCIBFBD1, coyote, MediaTek), US | Fire OS 8.0 / Android 11 |
| Hisense HG9 (HiSmart 4K A4FFM) | Android TV 9 |
| Hisense HiSmartTV A4 (xiaoyushan, MStar m7322), Android 11 firmware RTT2.221108.001 | Android TV 11 |
| Hisense HiSmartTV A4 (xiaoyushan, MStar m7322), India | Android TV 9 |
| Hisense SmartTV 4K FFM (huangshan, MediaTek), Latin America; also the US Hisense U9DG | Android TV 10 |
| Hisense SmartTV 4K FFM (lushan, MediaTek MT9676), Mongolia | Google TV 12 |
| Hisense SmartTV 4K FFM (songshan, MediaTek m7632); also the US Hisense 50A6H4 | Android 11 (SDK 30) |
| Hisense U75 / 75U75N (hengshan, MediaTek), Latin America and US | Google TV 12 |
| Hitachi 65QLEDSM20 (MTC, bangbae, MediaTek m7322), Australia | Android TV 11 |
| Hitachi CDH-LE32SMART21 (TCL-built C06, C06_2K_GB, MediaTek MT9221, 1 GB), Argentina | Android TV 11 |
| HOTACK SMART2KPJT projector, "Whale TV 95C8" (Amlogic am30b8, WhaleOS S8E, Zeasn firmware) | Android 13 |
| HY320 projector (t950s_be311, Google-certified Android TV) | Android 13 |
| INCLO Google TV 4K (KTC, Realtek, khardi, KTC_INCLO_4K_EU_23GV1), Taiwan | Google TV 11 |
| Insignia 42" F20 Fire TV (NS-42F201NA22, AFTEAMR311, blanche) | Fire OS 6.7.1.1 / Android 7.1.2 |
| Insignia 55" F30 4K Fire TV (NS-55F301NA25, AFTDCT31, duckie) | Fire OS 7 / Android 9 |
| JMGO N1S projector (K80A, holan, MediaTek m7332), Australia | Google TV 14 |
| JVC Android TV (Vestel-built, BARTU_Jvc, menlo, MediaTek mt5867), Spain | Android TV 11 |
| JVC LT-43KM348 (KTC-built, KTC_JVC_2K_25GV1, jagalchi, MediaTek mt5867 / T56) | Google TV 14 |
| JVC LT55MB508 (MTC, seocho, MediaTek MSD6886), Brazil | Android TV 11 |
| JVC UK 4K Android TV (MTC, MTCMSD6886UK, sadang, MediaTek m7322), UK | Android TV 11 |
| Kalley 43G200 (device R3_GTV, MOKA, Realtek RTD2841A), Latin America | Google TV 14 |
| Kalley R10G (device R10G, product ACER_FFM, MOKA, Realtek RTD2875P), Latin America | Google TV 14 |
| Karbonn SWTV-20AE (Skyworth OEM, SW4H, MediaTek), India | Android TV 10 |
| Kogan G7 (Expressluck, cottongreen, Realtek), Australia | Google TV (Android 11) |
| Kogan Google TV (Changhong CHiQ AI PONT, GTVU65KG, dangni, MediaTek mt5867), Australia | Google TV 14 |
| KTC 4K Smart TV (device hongkong, MediaTek), Taiwan | Google TV 14 |
| Liberton LTV 24H01AT (Liberton, built by Changhong, device redwood), Ukraine | Android TV 11 |
| Lumio Vision 7 55" (KTC ODM, Realtek, khardi, KTC_LUMIO_4K_EU_23GV1), India | Google TV 14 |
| Magcubic HY300 Pro projector (rk3566_rgo, device adt3, Askey) | Android 11 |
| Master-G MGA3200 (SCBC R1, Realtek; Android reports model `ATV R1`), Chile | Android TV 9 |
| Motorola / Coocaa SWTV-24AE-FHD (24LP6000G, Skyworth OEM, beomil), India/SEA | Google TV 14 |
| Motorola MT4310 (Newsan; Hisense board, hayward, MediaTek), Argentina | Android TV 11 |
| MTC UK 2K Android TV (tabata, MediaTek m5621; JVC-family firmware, `android.autoinstalls.config.JVC` present), UK | Android TV 11 |
| Next Start Pro 4K TV Box (SDT HPR314-Next, Realtek RTD1325), Turkey, streaming box | Google TV 14 |
| Noblex NoblexTV (Skyworth-built, shinjuku, MediaTek m5621), Argentina | Android TV 11 |
| Nokia Streaming Box 8000 (Sagemcom/NES, SEI600NK4, HND, Amlogic) | Android TV 14 |
| Nokia Streaming Box 8010 (Sagemcom/NES, SEI804NK, YYJ, Amlogic) | Android TV 14 |
| NVIDIA SHIELD TV 2017 (darcy) | Android TV 11 |
| NVIDIA SHIELD TV 2019 tube (P3430, sif) | Android TV 11 |
| NVIDIA SHIELD TV Pro 2019 (P2897, mdarcy) | Android TV 11 |
| OK. AI PONT (Changhong, ikebukuro, MediaTek), Italy; also Nikkei DVB91NK | Android TV 11 |
| OnePlus TV (C00, Cebu, MediaTek m7632), India | Android TV 10 |
| OnePlus TV (D00, Dubai) | Google TV 14 |
| OnePlus TV (Dosa, Oneplus_Dosa_IN) | Android TV 11 |
| OnePlus Y1 43" (Y Series, shibuya) | Android TV 11 |
| onn 4K Streaming Box (2023, Walmart; YOC, Amlogic), US | Google TV 14 |
| onn. 4K Plus Streaming (2025, Walmart; coffey, Amlogic S905X5M), US | Google TV 14 |
| Panasonic 4KTV-0SU (Skyworth-built SW4H, MediaTek m7332), Gulf region | Android TV 10 |
| Panasonic 4KTV-JUP (Jupiter_PA, MediaTek) | Android TV 11 |
| Panasonic Google TV LCD (OZUM_Panasonic, kowloon, MediaTek) | Android 14 |
| Panasonic TH-40HS550K (Skyworth-built, GS655 shibuya, MediaTek) | Android TV 11 |
| PANAVOX Google TV (dongnae) | Google TV 14 |
| PEAQ PTV 43AQU-5023C (Changhong, reports OK. AI PONT, dundas, MediaTek m7332) | Android TV 11 |
| Philips 50PUD7406/43 (TPV, PH0M_EA_T32, MStar/MediaTek), Chile; 55PUS7906/12, Lithuania | Android TV 11 |
| Philips 50PUF7099/T3 (TPV EBONY_V560, Hisilicon), China | Android TV 9 |
| Philips 50PUS8517/12 (TP Vision, PH1M_EA_9970A, MediaTek MT5895), Europe | Android TV 11 |
| Philips 55OLED707/12 (TP Vision, PH1M_EA_9970A, MediaTek MT5895), UK | Android TV 11 |
| Philips 55OLED708/12 (TPV, PH1M_WW_9972, MediaTek), Poland | Google TV 12 |
| Philips 55PUS7503/12 (TP Vision TPM171E, PH7M_EU_5596, MediaTek MT5891), Europe | Android TV 8.0 |
| Philips 65OLED706/12 (TP Vision, PH1M_EA_9970A, MediaTek MT5895), France; 55OLED706/12, Poland | Android TV 11 |
| Philips 65OLED907/12 (TP Vision, PH1M_EA_9970A, MediaTek MT5895), UK | Android TV 11 |
| Philips 65PML9506/12 (TP Vision, PH1M_EA_9970A, MediaTek), Spain | Android TV 11 |
| Philips 65PUS8102/12 (TP Vision TPM171E, PH7M_EU_5596, MediaTek MT5891), Europe | Android TV 8.0 |
| Philips 65PUS8729/60 (TPV, PH1M_WW_9972, MediaTek), Russia | Google TV 14 |
| Philips 65PUS9435/12 (TP Vision TPM191E, PH9M_EA_5599, MediaTek MT5887), Germany | Android TV 12 |
| Philips 75PUS8505/12 (TP Vision TPM191E, PH9M_EA_5599, MediaTek MT5887), Europe | Android TV 12 |
| Philips Google TV TA7 (TPV, PH3M_AL_T32, MediaTek), Brazil; 55PUD7908/77, Latin America | Google TV 14 |
| Philips R8G Google TV (MOKA, whitefield, Realtek RTD2851F), US | Google TV 14 |
| Philips Smart 4K TV (Skyworth, eleonas, MediaTek), India | Google TV 14 |
| Philips TPM191E / 50PUS8505 / 55OLED854 / 65OLED804 (TP Vision, PH9M_EA_5599, MediaTek MT5887), Europe | Android TV 12 |
| Polytron 2K (WLTV POLYTRON2K, hayward; Hisense/CVTE board, MediaTek mt5867), Indonesia | Android TV 11 |
| Portronics Beem 470 Smart LED Projector (Allwinner H713, 1 GB), India | Android 11 |
| PRISM+ Q65 Ultra 2026 QLED 4K AI Google TV (R10G, PRISMPLUS_FFM, MOKA, Realtek RTD2875 SoC, RTD6748 platform) | Google TV 14 |
| RCA 2K Android TV (KONKA, device lasalle, Realtek RTD2841A, ISDB) | Android TV 11 |
| RCA AI PONT SA (Changhong, redwood, ISDB92RC; also sold as realme 40-inch, AIWA AW32B4SMG, CHiQ AI PONT DVB92CQ and ERGO DVB92YC) | Android 14 |
| RCA RC50A21S-4KSM, Zeasn home (Toptech firmware RTK2851P, Realtek RTD2851P, board mac6p) | Android TV 9 |
| Redmi TV (MiTV-ANTL0, starwars) | Android 5.1.1 (not Android TV) |
| Redmi TV (MiTV-MFTP0, alita), China firmware 23.2.10.6129 | Android TV 9 (CN build) |
| RockTek GB1 | Google TV 14 |
| RockTek GX1 (HPR3A1325_4K) | Google TV 14 |
| Sankey SMART2KTVSE (Whale OS, SiliconPlayer am30bm) | Android TV 13 |
| Sankey SMART_TV (CVTE, HiSilicon Hi3751V350, 1 GB) | Android 12 |
| SANYO ATV R2 (SCBC, Realtek RTD285O) | Android TV 9 |
| Sber Salute TV 43" (CVTE TP.T962D4.PC756, Amlogic T962D4, at30a6, 2 GB), Russia | StarOS 1.101 / Android 13 |
| Seleco TV39020HDA9SMART 39" (AT_SmartScreen, Toptech + Zeasn, Realtek RTK2842P), Italy | Android TV 9 |
| SHARP 2T-C32GH3000I (SHARP 2K Smart TV-A; Skyworth-built, beomil, MediaTek mt5867), Indonesia | Google TV 14 |
| SHARP AQUOS TVE21F (AQUOS-TVE21F, banqiao, CVTE/MediaTek), UK | Android TV 14 |
| SHARP AQUOS TVE23A (AQUOS-TVE23A, sandiego, CVTE/MediaTek mt5889), Denmark | Google TV 14 |
| Skyworth / Coocaa 8R40_K5C (Realtek rtd285x, 1 GB, 32-bit), China domestic firmware | Android 8.0 (not Google TV) |
| Skyworth 65A3F (7T641_A3F, MediaTek MT9633), China | Android 13 (CCOS 10.01.250328) |
| Skyworth 7S60_A5 (sky8S34, 1.9 GB), China market | Coocaa OS / Android 8 |
| Skyworth 7T861_A23 (MediaTek MT9638), China market | Coocaa OS / Android 10 |
| Skyworth Coocaa K3 Pro 90P3D Max (8R145_P3DM, Realtek RTD2885N), China market | Android 11 (Coocaa OS) |
| Skyworth SW-22SA (SW6H, MediaTek), ~1.9 GB | Android TV 14 (Google TV home) |
| Skyworth SWTV-21AE (sunnyvale, MediaTek) | Android TV 14 |
| Skyworth SWTV-22AE-FHD (umeda, MediaTek), Vietnam/Global | Google TV 14 |
| Skyworth SWTV-24AE-4K NF (eleonas, MediaTek mt5896), South Africa/Global | Google TV 14 |
| Skyworth SWTV-24NA-4K NF (eleonas, MediaTek mt5896), US | Google TV 14 |
| Skyworth Xiaopai P3 (Q5001, cupid-p2), streaming box | Android 10 |
| Sony BRAVIA 4K AE1 (BRAVIA_AE_M6L, MediaTek MT5897), EU | Google TV 12 |
| Sony BRAVIA 4K AE2 (BRAVIA_AE2, MediaTek MT5897), North America | Google TV 14 |
| Sony BRAVIA 4K GB (BRAVIA_ATV2_EU, BRAVIA_ATV2) | Android TV 9 |
| Sony BRAVIA 4K GB (BRAVIA_ATV2_UC, MediaTek mt5891) | Android TV 9 |
| Sony BRAVIA 4K UR2 (BRAVIA_UR2_4K, MediaTek) | Android TV 10 |
| Sony BRAVIA 4K UR3 (BRAVIA_UR3, MediaTek mt5893), EU | Android TV 10 |
| Sony BRAVIA 4K VH1 (KD-55XH9096, BRAVIA_VH1, MediaTek MT5895), EU | Google TV 12 |
| Sony BRAVIA 4K VH2 (KD-55X80J / XR-65A83J, BRAVIA_VH2, MediaTek), EU, North America and India | Google TV 12 |
| Sony BRAVIA 4K VH21 (KD-65X85K, 2022, BRAVIA_VH21, MediaTek MT5895), Australia | Google TV 12 |
| Sony BRAVIA 4K VH21 (XR-55A80K, 2022, BRAVIA_VH21, MediaTek mt5895), EU | Google TV 12 |
| Sony BRAVIA 9 II (K-65XR90M2, BRAVIA_AE3, MediaTek MT5838) | Google TV 14 |
| Sony BRAVIA BF1 (BRAVIA 2 II class, BRAVIA_BF1_4K_A, MediaTek) | Google TV 12 |
| Sony BRAVIA KD-43X8000G (2019, BRAVIA_ATV3_4K, MediaTek), APAC | Android TV 9 |
| Sony BRAVIA KD-43X80L (2023, BRAVIA_VH22, MediaTek MT5895), EU | Google TV 12 |
| Sony BRAVIA KD-49X8307C (2015, "BRAVIA 4K 2015", SVP-DTV15, MediaTek), EU firmware | Android TV 7.0 |
| Sony BRAVIA KD-55X75K (BRAVIA_VU3_4K, Realtek rtd2885m) | Google TV 14 |
| Sony BRAVIA KD-55X8000C (2015, "BRAVIA 4K 2015", SVP-DTV15, MediaTek), China firmware | Android TV 7.0 |
| Sony BRAVIA KD-55X8509C (2015, "BRAVIA 4K 2015", SVP-DTV15, MediaTek), EU firmware | Android TV 7.0 |
| Sony BRAVIA KD-55X85J (2021, BRAVIA_VH2, MediaTek MT5895), South America | Google TV 12 |
| Sony BRAVIA KD-55X9500G (BRAVIA_UR2_4K_CN), China | Android TV 10 |
| Sony BRAVIA KD-55XE8596 (2017, "BRAVIA 4K GB", BRAVIA_ATV2_EU, MediaTek MT5891), UK | Android TV 9 |
| Sony BRAVIA KD-55XF7596 (2018, "BRAVIA 4K GB ATV3", BRAVIA_ATV3_4K_EU, MediaTek MT5891), EU | Android TV 9 |
| Sony BRAVIA KD-55XF8096 (2018, "BRAVIA 4K GB ATV3", BRAVIA_ATV3_4K_EU, MediaTek MT5891), Spain | Android TV 9 |
| Sony BRAVIA KD-65X80J (2021, BRAVIA_VH2, MediaTek), India | Google TV 10 |
| Sony BRAVIA KD-65X82L (2023, BRAVIA_VH22, MediaTek), India | Google TV 12 |
| Sony BRAVIA KD-65X9300D (2016, SVP-DTV15, MediaTek), Asia-Pacific | Android TV 8.0 |
| Sony BRAVIA KDL-43W950D (2015/2016, SVP-DTV15, MediaTek), India ("PA") firmware | Android TV 8.0.0 |
| Sony BRAVIA TL2 (OD0M_EA_T32, TPV-built, MediaTek), Europe | Android TV 11 |
| Sony BRAVIA VU1 (KD-43X7500H, BRAVIA_VU1_UC, Realtek) | Android TV 11 |
| Sony BRAVIA VU31 4K (BRAVIA_VU31_4K, Realtek), India | Google TV 14 |
| Sony BRAVIA X9000H (2020, BRAVIA_VH1_CN, MediaTek MT5895), China | Android TV 12 |
| Sony BRAVIA XBR-55X850C (2015, "BRAVIA 4K 2015", SVP-DTV15, MediaTek), US firmware | Android TV 8.0 |
| Sony BRAVIA XBR-55X850D (Android model "BRAVIA 4K 2015"), firmware 6.827 | Android TV 8.0.0 |
| Sony BRAVIA XBR-65X900E (2017, BRAVIA_ATV2) | Android TV 9 |
| Sony BRAVIA XBR-65X900F (2018, BRAVIA_ATV3_4K, MediaTek MT5891), US/Canada | Android TV 9 |
| Sony KD-55X7500 (BRAVIA 4K GB ATV3, BRAVIA_ATV3_4K_CN), China | Android TV 9 |
| Sony XR-65A80J (65-inch OLED, 2021, BRAVIA_VH2), EU | Google TV 12 |
| Sony XR-65X90J (2021, BRAVIA_VH2, MediaTek MT5895), China | Android TV 10 |
| Stellamore T950S projector (Droidlogic be30a3, Amlogic txhd2) | Android 13 |
| Super General 55" 4K (CVTE TP.SK708D.PC821, Amlogic T972, x30a4, 1 GB), Saudi Arabia | Android 9 |
| Síragon 4K Smart TV (KTC, MediaTek MT9676, elliniko, KTC_SIRAGON_4K_24GV1), Venezuela | Google TV 14 |
| TCL / FFalcon China (tcl9655_cn, MediaTek MT9655 "merak", UTT2.250416.001 AU01) | Android 14 |
| TCL 32ES570F (C06, C06_2K_GB, MStar mt5867, 939 MB), Italy | Android TV 11 |
| TCL 32L5A (C06, model "Smart TV", 1 GB) | Android TV 11 |
| TCL 32S5400A (C06, C06_2K_GB, MediaTek MT9221, 1 GB), 2023 | Android TV 11 |
| TCL 32S5K (C06, C06_2K_GB, MediaTek MT9221, 918 MB), EU | Android TV 11 |
| TCL 32SF560 (TCL Fire TV, AFTSHN02, shine) | Fire OS 7.7.1.7 / Android 9 |
| TCL 40S5400A (C06, C06_2K_GB, MediaTek MT9221, 1 GB) | Android TV 11 |
| TCL 43C645 (Smart TV Pro, Realtek RTD2851 / R51MT05, V8-R51MT05-LF1V652); also TCL 50C635 / 60C635 (G03_4K_GB, Realtek rtd288o), Spain | Google TV launcher on Android TV 11 |
| TCL 43EP660 (Europe, BeyondTV, Realtek rtd285o, V8-R851T02-LF1V378) | Android TV 9 |
| TCL 43P615 (Europe, BeyondTV, Realtek RTD2851, AR06) | Android TV 11 |
| TCL 43P725 (Smart TV Pro, BeyondTV4, Realtek rtd288o), Italy | Android TV 11 |
| TCL 43S450F-CA (S4 Fire TV Edition, AFTDEC012E, ginza, MediaTek), Canada | Fire OS 8.1.8.5 / Android 11 |
| TCL 43S5400A (C06, C06_2K_GB, MediaTek MT9221, 1 GB) | Android TV 11 |
| TCL 43S5K / 32V5C (G13, G13_2K_GB, MediaTek MT9221/MT5867, 1 GB), India; also a G13 unit from Brazil and a 40" V5C on AU13 | Google TV 14 |
| TCL 50C725K (Smart TV Pro, BeyondTV4/BeyondTV6, RTD2851M, Realtek rtd288o, 2 GB), AR10 build | Android TV 11 |
| TCL 50DP660 / L49C2US ("Percee TV", tcl_eu, MediaTek MT5658 / board mt5891, V8-T658T01-LF1V602 / V570) | Android TV 8.0 |
| TCL 50EP680 (Europe, BeyondTV, Realtek rtd285o) | Android TV 11 |
| TCL 50J7000A (Thailand, BeyondTV, Realtek RT2851 / rtd285o, AR04, V8-R851T02-LF1V674) | Android TV 11 |
| TCL 50P615 (Greece and Türkiye, BeyondTV, Realtek RTD2851 / rtd285o, AR05) | Android TV 11 |
| TCL 50P735 (BeyondTV4, Smart TV Pro) | Google TV 11 |
| TCL 55" 4K Google TV, 2023 (G10, G10_4K_US_NF); also TCL 55P6K, Spain and TCL 43P69K, Netherlands (G10_4K_GB_NF_32BIT) | Google TV 12 |
| TCL 55C715 QLED (Europe, BeyondTV2, Realtek RTD2851, V8-R851T02-LF1V606) | Android TV 9 |
| TCL 55C725 (Smart TV Pro, BeyondTV4, G03_4K_GB, Realtek R51MT05, V8-R51MT05-LF1V620), Italy; also TCL 65C655 Pro (same build) | Google TV launcher on Android TV 11 |
| TCL 55C745, 2023 (G08, G08_4K_GB, MediaTek tcl9618) | Google TV 14 |
| TCL 55C7K QD-Mini LED, 2025, UK (G08, MediaTek tcl9618) | Google TV 14 |
| TCL 55C815 (2020, Europe/Denmark, BeyondTV2, Realtek rtd285o) | Android TV 11 |
| TCL 55EP640 (Greece, BeyondTV, Realtek RTD2851 / rtd285o, AR04) | Android TV 11 |
| TCL 55P638 (Smart TV, BeyondTV4) | Android TV 11 |
| TCL 55P7K, Middle East (G10, G10_4K_GB, Realtek RTD2875P) | Google TV 12 |
| TCL 55P8LS QD-Mini LED, Vietnam (G10, G10_4K_GB_32BIT, Realtek rtd6748) | Google TV 12 |
| TCL 55Q10G Pro (China, tcl_mt5879_cn, MediaTek MT9952) | Android TV 11 |
| TCL 55Q6CX1 (Smart TV Pro, G08, G08_4K_GB, MediaTek) | Google TV 12 |
| TCL 55S470G (G10, G10_4K_US_NF, Realtek), US | Google TV 12 |
| TCL 55S546 (5-Series, G03_4K_US, BeyondTV4, GTV93), US | Google TV 11 |
| TCL 58S571F (TCL Fire TV, AFTDEC012E, ginza, MediaTek mt5896), US | Fire OS 8 / Android 11 |
| TCL 65C725 (Smart TV Pro, BeyondTV4) | Android TV 11 |
| TCL 65C735 (Smart TV Pro, G05, MediaTek), France; also TCL 55C728, Turkey, TCL 65C935, France, and TCL 65C745, France | Google TV 11 |
| TCL 65C7K (Smart TV Pro, G08, G08_4K_GB, MediaTek mt5896), Brazil | Google TV 14 |
| TCL 65C7L (Smart TV Pro, G08, G08_4K_GB, MediaTek tcl9618), 2025, Australia | Google TV 14 |
| TCL 65EP640 (Finland, BeyondTV, Realtek RTD2851 / rtd285o, AP06) | Android TV 9 |
| TCL 65EP68 (Europe, BeyondTV, Realtek RTD2851 / rtd285o, V8-R851T02-LF1V722) | Android TV 11 |
| TCL 65Q550G (TCL Smart TV, Google TV 11, V8-R51MT08-LF1V095.021344) | Google TV |
| TCL 65Q650G (US Q6 2023, BeyondTV4, Realtek RTD2851 / R51MT08, V8-R51MT08-LF1V095, 2 GB) | Google TV 11 |
| TCL 65X10 QLED (France, BeyondTV2, Realtek RTD2851, V8-R851T02-LF1V363) | Android TV 9 |
| TCL 75C845X2, UK/Europe (G08_4K_GB, MediaTek tcl9618) | Google TV 14 |
| TCL 75P8K, Europe / Nordics (G10, G10_4K_GB / C6KS, Realtek RTD2875P / rtd6748) | Google TV 12 |
| TCL 75T7 / 55Q77K (Smart TV Pro, G08, G08_4K_US, MediaTek tcl9618), US firmware | Google TV 14 |
| TCL 85RM7L (G08, G08_4K_GB, MediaTek tcl9618) | Google TV 14 |
| TCL ak30a5 (China, Amlogic) | Android TV 9 |
| TCL BeyondTV (India) | Android TV 9 |
| TCL BeyondTV, EU/Nordic (Realtek RTD2851, V8-R851T02-LF1V606) | Android TV 9 |
| TCL BeyondTV, Poland (Realtek RTD2851, AP03, 2 GB) | Android TV 9 |
| TCL BeyondTV2 (Latin America / Brazil, Realtek RTD2851) | Android TV 9 |
| TCL BeyondTV2, Android 11 build (AR05, Realtek rtd285o) | Android TV 11 |
| TCL C635 (Smart TV Pro, BeyondTV4, Realtek rtd288o, Android 11/SDK 30), Brazil | Android TV 11 |
| TCL C755 QD-Mini LED, 2024, Australia (G08, MediaTek tcl9618) | Google TV 14 |
| TCL C89B 85", 2024, a local variant of the C855 (G08, G08_4K_GB, MediaTek tcl9618) | Google TV 14 |
| TCL L40S66E (C06, C06_2K_GB, MediaTek MT9221, 1 GB), Argentina | Android TV 11 |
| TCL Q7C (Smart TV Pro, G08, G08_4K_GB, MediaTek tcl9618), European firmware | Google TV 14 |
| TCL QM6K Pro, 2025 (G08, MediaTek tcl9618) | Google TV 14 |
| TCL Smart TV (G07, G07_4K_GB_NF, Realtek RTD2851A, 2 GB) | Google TV 11 |
| TCL Smart TV Pro (G03, G03_4K_GB / G03_4K_US, BeyondTV4, Realtek R51MT05, V8-R51MT05-LF1V620 / LF1V813), Middle East and US firmware | Google TV 11 |
| TCL Smart TV Pro (G05, G05_4K_US, MediaTek MT5889); also TCL 55Q750G | Google TV 11 |
| TCL Smart TV Pro (G08, G08_4K_GB, MediaTek), Australian firmware | Google TV 12 |
| TCL Smart TV Pro (G08, G08_4K_GB, MediaTek), Europe/UK firmware | Android TV 12 |
| TCL Smart TV Pro (G09, G09_4K_GB, Amlogic T5M, 2 GB), UK firmware, Brazil | Google TV 12 |
| TCL Smart TV Pro, UK Freeview Play (BeyondTV4 / BeyondTV6, Realtek rtd288o, 2 GB, AR05) | Android TV 11 |
| TCL tc8000_ay30a2 (China, Amlogic ohm, TC8000, 3.8 GB) | Android 11 |
| TCL UnionTV, Latin America (TCL-built like Thomson 32HE5800, Realtek RT2841) | Android TV 9 |
| Tele System TS ON T2/HD (ONT2HD, SDMC, Amlogic), Italy | Android TV 11 |
| Thomson 32HE5800 (TCL-built UnionTV, Realtek RT2841), Spain | Android TV 9 |
| Thomson 43RT1055 FA Series (SPPL-built, sauve, Realtek RTD2841A), India | Android TV 14 |
| Thomson SPPL 4K Google TV (Skyworth SW6H, MediaTek, same board as `skyworth-sw-22sa/`), India, ~1.9 GB | Android TV 14 (Google TV home) |
| Thomson T32RTL6000 (SCBC, Realtek; Android reports model `ATV R1`) | Android TV 9 |
| TiVo Stream 4K (SEI Robotics IPA1104HDW, SEI400TV, Amlogic S905Y2, NES) | Android TV 12 |
| Tornado 65UA1400E (El Araby, Skyworth-built SW4H, MediaTek m7332), Egypt | Android TV 11 |
| Toshiba (Vestel Cosmos170, yeoksam, MediaTek), Spain | Android TV 11 |
| Toshiba Fire TV (AFTHA004, hazel) | Fire OS 7 / Android 9 |
| Turkcell TV+ 4K Box (SDMC DV6069Y, Amlogic), Turkey, operator streaming box | Android TV 10 |
| Tuvio TD43UFBCV5 (Changhong AI PONT, DVB96, MediaTek m7632), Russia | Google TV 14 |
| Verizon Stream TV, 1st gen (WNC JS8V, Amlogic), US | Android 12 |
| Vidda C1S 4K laser projector (VIDAA_TV, device MT9669, board m7642), China JuUI | Android 9 |
| Vidda VIDAA_TV (Hisense sub-brand; device MT9653, board mt5879), China JuUI | Android TV 11 |
| Vision 2024VISION43STV (CVTE TP.T950D4T.PB751, Amlogic T950D4, 1 GB), Europe | Android 13 |
| Vu 4K (HVCL, lushan, MediaTek), India | Google TV 12 |
| Vu Premium FHD 43-inch (Smart 2K ATV4, hamamatsucho), India | Android TV 9 |
| Vu VIBE TV (KONKA, bandra, Realtek), India | Google TV 14 |
| Wanbo X5 Pro projector (built-in Google TV stick: SkyworthDigital YYT, Amlogic), Australia | Google TV 14 |
| XGIMI Horizon S Pro projector (XK03H, xgimigalileo, MediaTek) | Android TV 11 |
| XGIMI MoGo 2 projector (MoGo, xgimiapollo, pokemon) | Android TV 11 |
| XGIMI MoGo 3 Pro projector (G0035, altai, MediaTek) | Google TV 14 |
| XGIMI MoGo 4 Laser projector (WK03K, altai, MediaTek) | Google TV 14 |
| XGIMI MoGo 4 projector (WK08K, altai, MediaTek) | Google TV 14 |
| Xiaomi Mi Box 3 (MIBOX3, once), streaming box | Android TV 9 |
| Xiaomi Mi Box S (MIBOX4, oneday), streaming box | Android TV 9 |
| Xiaomi Mi Box S (MIBOX4, oneday), streaming box, Android 12 update | Android TV 12 |
| Xiaomi Mi Box S 2nd gen (MiTV-AFKR0, jaws), streaming box | Google TV 11 |
| Xiaomi Mi TV (MiTV-MOOQ0, croods; includes P1 43" EU variant) | Android TV 10 and 11 |
| Xiaomi Mi TV 4A (MiTV-MOOQ1) | Android TV 10 |
| Xiaomi Mi TV P1 43" (MiTV-MOOQ2, croods) | Android TV 11 |
| Xiaomi MiTV-AESP0 stick (aquaman, 1 GB) | Android TV 9 |
| Xiaomi MiTV-ANSP0 (小米电视5 Pro 65", jobs, Amlogic), China firmware 23.6.5.3226, 4 GB RAM | Android TV 9 (CN build) |
| Xiaomi MiTV-ASTP0 (mulan, sold as L55M7-EA), China firmware 22.12.2.1630, 1.5 GB RAM | Android TV 9 (CN build) |
| Xiaomi MiTV-ASTP1 (maverick), China firmware 2.40.641.0514.cn, 1 GB RAM, 720p | Android TV 9 (CN build) |
| Xiaomi MiTV-AXFR0 (rango, Amlogic, 1 GB), India | Android TV 11 |
| Xiaomi MiTV-AXFR1 (rango, Amlogic, 1 GB), India | Android TV 11 |
| Xiaomi MiTV-AXSO0 (dangal; Mi TV 4C Pro 2019 / 4A Pro) | Android TV 9 |
| Xiaomi MiTV-AXSO2 (dangalUHD, India, 2 GB) | Android TV 9 |
| Xiaomi MiTV-MFTR0 (freeguy, MediaTek mt9653), China firmware OS2.0.23.0.RFTMATV (HyperOS TV 2) | Android 11 (HyperOS TV 2, CN build) |
| Xiaomi MiTV-MOOR2 / MOOR4 (venom, MediaTek) | Google TV 11 |
| Xiaomi MiTV-MOSR4 (irobot, MediaTek) | Google TV 11 |
| Xiaomi MiTV-MSSP0 / Xiaomi Mi TV 4A 32 (amelie), firmware PI.5926 | Android TV 9 |
| Xiaomi MiTV-MSSP1 (nino), firmware PI.5925 | Android TV 9 |
| Xiaomi MiTV-MSSP2 (volver, MediaTek), firmware PI.4621, low-RAM | Android TV 9 |
| Xiaomi MiTV-MSSP3 (machuca, MediaTek) | Android TV 11 |
| Xiaomi MiTV-MSSP3 (machuca, MediaTek), Android 9 firmware PI.7524 | Android TV 9 |
| Xiaomi Redmi Smart TV A Pro 55" 2025 Energy-saving (MiTV-ASTR0, blueplanet, Amlogic), China firmware OS3.0.106.0.USTAATV, 3 GB RAM | Android 14 (HyperOS 3, CN build) |
| Xiaomi TV A Pro 75" 2026 (MiTV-MZTU0, river, MediaTek) | Google TV 14 |
| Xiaomi TV Box S 3rd gen (MiTV-AFMU0, twilight, Amlogic), streaming box | Google TV 14 |
| Xiaomi TV EA Pro 55" (MiTV-MTEQ0, baymax, MediaTek), China firmware 23.6.26.4028 | Android TV 10 (CN build) |
| Xiaomi TV S Mini LED 75 2025 (MiTV-MOEU0, ladybird, MediaTek) | Google TV 14 |
| Xiaomi TV S Pro 100 Mini LED 2025 (小米电视 S Pro 100, L100MB-SP, MiTV-MFFU0, finch, MediaTek), China firmware OS3.0.115.0.UFFMATV, 4 GB RAM | Android 14 (HyperOS 3, CN build) |
| Xiaomi TV Stick 4K (MiTV-AYFR0, soul, Amlogic), streaming stick | Android TV 14 |
| Xiaomi TV Stick 4K 2nd gen (MiTV-AFMU1, twilight, adastra, Amlogic), streaming stick | Google TV 14 |

<!-- tv-table:end -->

</details>

Your TV not here? The prompt discovers the bloat itself. Add your list afterwards and the next
person with your model skips that step: [CONTRIBUTING.md](CONTRIBUTING.md).

## Licence

MIT, see [`LICENSE`](LICENSE). The Outfit typeface is under the SIL Open Font License, see
`launcher/FONT-LICENSE-OFL.txt`.

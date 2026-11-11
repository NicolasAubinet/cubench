# Cubench

<a href="https://play.google.com/store/apps/details?id=com.cube.nanotimer&referrer=utm_source%3Dgithub" target="_blank">
  <img alt="Get it on Google Play"
       src="https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png"
       height="60"/>
</a>

A free, open-source speedcubing timer and trainer for Android, formerly Nano Timer, on Google Play since 2014. No ads, no account: your solves stay on your phone.

## Timer

* Scrambles for 2x2 to 7x7, Megaminx, Pyraminx, Skewb, Square-1, Clock and FTO
* Random-state scrambles for 3x3, 2x2, Pyraminx, Square-1 and FTO
* Special scrambles to practice only F2L, the last layer, OLL, PLL and more
* Your own solve types, to keep separate histories for one-handed, blindfolded or anything else on the same puzzle
* Multi-step timing, to see which step of your solve takes the most time
* WCA inspection, +2 and DNF
* Ao5, Ao12, Ao50, Ao100 and more, personal bests, graphs and sessions
* Blind solve types with success rate and Mo3
* Backup and restore, CSV export
* In English, French, Spanish and Brazilian Portuguese

## Smart cubes (new in 2.2)

Connect a Bluetooth smart cube and Cubench follows every move:

* A live 3D cube that turns with yours
* Every solve reconstructed step by step, with the time spent recognizing and turning: CFOP, Roux, beginner method, and blind solves with their buffers and targets
* Each algorithm case detected and tracked across your solves: known, needs review, to learn
* Drills for any PLL, OLL or F2L case, checked on the real cube rep by rep
* A look-ahead drill, and a cross drill that compares your cross with the shortest one

Supported cubes:

* **GAN**: 356 i3, i Carry, i Carry S, i Carry 2, GAN12 ui, GAN12 ui Maglev, GAN14 ui FreePlay, Mini ui FreePlay, Monster Go 3Ai
* **MoYu**: AI 2023, WeiLong V10 AI, WeiLong V11 AI
* **QiYi**: QiYi Smart Cube, XMD Tornado V4 AI

Own a smart cube that isn't listed, or one that misbehaves? Please [open an issue](../../issues): logs from cubes I don't own are the most useful bug reports there are.

## For developers

Java, built with Gradle. Modules:

* `nanotimer`: the app (GUI, scramblers, reconstruction, drills)
* `smartcube`: the Bluetooth protocols for GAN (gen 2, 3 and 4), MoYu and QiYi cubes, with no third-party library
* `coremodel`: the model shared by the app and the coach service
* `services`: SQLite persistence
* `datamodel`: value objects, puzzle and solve types, statistics
* `guilib`: shared drawables, styles and themes

```sh
./gradlew assembleDebug        # build the debug APK
./gradlew test                 # unit tests, all modules
```

## Acknowledgements

* The FTO (Face-Turning Octahedron) random-state scrambler is a Java port of the solver from [cstimer](https://github.com/cs0x7f/cstimer) by Shuang Chen (cs0x7f) and contributors (`src/js/solver/ftocta.js` and the supporting `mathlib.js`), used under the GPL-3.0 license.

## Support

Cubench is free, open source and has no ads. If it helps your cubing and you'd like to say thanks, you can buy me a coffee on [Ko-fi](https://ko-fi.com/nicolasaubinet). It unlocks nothing, it just helps me keep working on it.

## License (GNU GPL v3)

    Copyright (C) 2016  Nicolas Aubinet

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.

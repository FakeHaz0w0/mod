# Yandere Entity (Forge 1.20.1)

An obsessive yandere that stalks you, leaves signs and gifts, approaches from behind, guards you when its
affection is low, and becomes your devoted companion when it loves you.

## Get the .jar without installing anything (GitHub Actions)
1. Make a free GitHub account and create a new **public** repository.
2. Unzip this project. On the repo page choose **Add file > Upload files** and drag in everything inside the
   `yandere-mod` folder (there are no hidden files in this zip). Click **Commit changes**.
3. Still on the repo page choose **Add file > Create new file**. In the name box type exactly
   `.github/workflows/build.yml` (typing the slashes makes the folders). Open `build-workflow.yml` from this
   project, copy all of it into the big text box, and click **Commit changes**.
4. Open the **Actions** tab. The "Build mod jar" run takes about 3-6 minutes.
5. Open the finished run, download the **yanderemod-jar** artifact (a zip) and unzip it to get `yanderemod-1.0.0.jar`.
6. Put the jar in the `mods` folder of a Forge 1.20.1 install. If the run fails, send me the red error text.

## Build locally instead
1. Install **JDK 17**.
2. In this folder run `./gradlew build` (Windows: `gradlew.bat build`).
3. The mod jar appears in `build/libs/yanderemod-1.0.0.jar`. Drop it in the `mods` folder of a **Forge 1.20.1 (47.x)** install.
4. To test from source instead: `./gradlew runClient`.

## Testing commands (need op)
`/yandere status` | `/yandere affection set 80` | `/yandere affection add -20` | `/yandere approach`
`/yandere phase companion` | `/yandere phase stalking` | `/yandere obsession <player>` | `/yandere respawn` | `/yandere reset`

A Yandere Spawn Egg is in the Spawn Eggs creative tab (extra bodies remove themselves, only one may exist).

## Where to tweak things
* `config/yanderemod-common.toml`: all timings, thresholds (60 / 30 / 75), affection per gift, toggles.
* `util/YandereLines.java`: every chat line, sign text and love letter.
* `data/YandereData.java`: the saved mood/progress.
* `entity/YandereEntity.java`: all behaviour.
* Skin: `assets/yanderemod/textures/entity/yandere.png` (classic arms; for slim skins see `YandereRenderer`).

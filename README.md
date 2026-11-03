# Будильник смен

Android-приложение (только Android, без Kotlin Multiplatform) для графика смен и одного следующего будильника. Идентификатор `com.walhalla.shiftalarm`. Сеть, логин, реклама и Firebase не используются.

Архитектура, контракты и матрица разрешений: [ARCHITECTURE.md](ARCHITECTURE.md).

Код написан заново. FossifyOrg/Clock (GPL-3.0) и iTroy0/ShiftCalendar (MIT) можно смотреть только как идею, их исходники сюда не копировались.

## Сборка

Нужны JDK 17+ и Android SDK с `platforms;android-36` и `build-tools;36.0.0`.

```bash
export ANDROID_HOME="$HOME/android-sdk"
./gradlew assembleDebug
./gradlew :core:model:test
```

Отладочный пакет: `app/build/outputs/apk/debug/app-debug.apk`.

## Подпись release

`release` подписывается только если заданы все четыре переменные окружения. Иначе подпись пропускается, пароли в репозиторий не попадают.

- `SHIFT_KEYSTORE_PATH`
- `SHIFT_KEYSTORE_PASSWORD`
- `SHIFT_KEY_ALIAS`
- `SHIFT_KEY_PASSWORD`

Контакт-заглушка: <username> · https://example.com

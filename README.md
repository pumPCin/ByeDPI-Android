# ByeDPI Android 

<div style="text-align: center;">
  <img alt="Логотип ByeDPI" src="https://github.com/dovecoteescapee/ByeDPIAndroid/blob/master/.github/images/logo.svg" width="100%" height="200px">
</div>

---

Приложение для Android, которое локально запускает ByeDPI и перенаправляет весь трафик через него.

Для стабильной работы может потребоваться изменить настройки. Подробнее о различных настройках можно прочитать в [документации ByeDPI](https://github.com/hufrea/byedpi/blob/v0.13/README.md).

Приложение не является VPN. Оно использует VPN-режим на Android для перенаправления трафика, но не передает ничего на удаленный сервер. Оно не шифрует трафик и не скрывает ваш IP-адрес.

Приложение является форком [ByeByeDPI Android](https://github.com/romanvht/ByeDPIAndroid) 

Оригинальное приложение [ByeDPI Android](https://github.com/dovecoteescapee/ByeDPIAndroid)

---

### Возможности
* Автозапуск сервиса при старте устройства
* Сохранение списков параметров командной строки
* Улучшена совместимость с Android TV/BOX
* Раздельное туннелирование приложений
* Импорт/экспорт настроек

### Использование
* Для работы автозапуска активируйте пункт в настройках.
* Рекомендуется подключится один раз к VPN, чтобы принять запрос.
* После этого, при загрузке устройства, приложение автоматически запустит сервис в зависимости от настроек (VPN/Proxy)
* Если у вас Android TV/BOX, и при подключении пропадает соединение по Ethernet, активируйте режим белого списка и укажите нужные приложения, которые должны работать через VPN (например, YouTube)
* Комплексная инструкция от комьюнити [ByeByeDPI-Manual](https://github.com/HideakiTaiki/ByeByeDPI-Manual)

### Как использовать ByeDPI вместе с AdGuard?
* Запустите ByeDPI в режиме прокси.
* Добавьте ByeDPI в исключения AdGuard на вкладке "Управление приложениями".
* В настройках AdGuard укажите прокси:
```plaintext
Тип прокси: SOCKS5
Хост: 127.0.0.1
Порт: 10080 (по умолчанию)
```

### Сборка
1. Клонируйте репозиторий с сабмодулями:
```bash
git clone --recurse-submodules
```
2. Запустите скрипт сборки из корня репозитория:
```bash
./gradlew assembleRelease
```
3. APK будет в `app/build/outputs/apk/release/`

> P.S.: hev_socks5_tunnel не соберется под Windows, вам нужно будет использовать WSL

### Зависимости
- [ByeDPI](https://github.com/hufrea/byedpi)
- [hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel)

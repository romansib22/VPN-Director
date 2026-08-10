# VPN-Director
Система управления OpenVpn сервером.

Возможности:
 - Редактирование конфига OpenVpn-сервера
 - Добавление маршрутов для подсетей и для отдельных IP по доменному имени, которые будут отправлены клиенту (push route)
 - Выпуск сертификатов для клиентов
 - Просмотр выпущенных сертификатов
 - Генерация конфигурационных файлов клиентов
 - Просмотр активных соединений
 - Перезапуск Openvpn-сервиса

## Требования
Java 11

## Настройка
Настройки приложения хранятся в `.env`-файле, который должен лежать рядом с `.jar`-файлом.

Необходимо скопировать `.env_example` в `.env` и отредактировать его в соответствии со своим окружением.

### Переменные окружения (.env)

| Переменная | По умолчанию | Описание |
|------------|--------------|----------|
| `SERVER_PORT` | `8080` | Порт HTTP-приложения |
| `SERVER_CONTEXT_PATH` | `/` | Базовый путь (если nginx проксирует не с корня) |
| `SERVER_TIMEZONE` | `3` | Смещение от UTC в часах для отображения времени (3 = UTC+3) |
| `OPENVPN_ROOT` | `/etc/openvpn` | Каталог OpenVPN |
| `OPENVPN_CONFIG_FILE_NAME` | `server.conf` | Имя конфигурационного файла сервера |
| `OPENVPN_CONFIG_ENCODING` | `Windows-1251` | Кодировка конфига; пустое значение — автоопределение |
| `OPENVPN_EASY_RSA_PATH` | `/etc/openvpn/easy-rsa/2.0` | Путь к easy-rsa |
| `OPENVPN_STATUS_FILE_NAME` | `openvpn-status1194.log` | Имя файла статуса (в каталоге `OPENVPN_ROOT`) |
| `OPENVPN_SYSTEMD_UNIT_PREFIX` | `openvpn-server` | Префикс systemd unit для перезапуска |
| `SECURITY_USER_NAME` | `admin` | Логин веб-интерфейса |
| `SECURITY_USER_PASSWORD` | — | Пароль веб-интерфейса |

#### Часовой пояс (`SERVER_TIMEZONE`)

Смещение от UTC в **целых часах** для отображения времени подключений и длительности сессий.

| Значение | Пояс |
|----------|------|
| `3` | UTC+3 (Москва) |
| `0` | UTC |
| `-5` | UTC−5 |

Для CSV v3 время в status-файле считается **локальным временем сервера** — задайте `SERVER_TIMEZONE` в соответствии с часовым поясом хоста OpenVPN. Для legacy-формата время в файле трактуется как UTC и переводится в указанный пояс.

#### Файл статуса (`OPENVPN_STATUS_FILE_NAME`)

Полный путь к файлу: `{OPENVPN_ROOT}/{OPENVPN_STATUS_FILE_NAME}` (например, `/etc/openvpn/openvpn-status1194.log`).

Имя файла должно совпадать с директивой `status` в конфиге OpenVPN. Поддерживаются оба формата status-файла:
- **legacy** (OpenVPN до 2.5): секция `OpenVPN CLIENT LIST`, строка `Updated,...`
- **CSV v3** (OpenVPN 2.5+): строки `TIME,...`, `CLIENT_LIST,...` — формат по умолчанию в OpenVPN 2.6

#### Перезапуск сервиса (`OPENVPN_SYSTEMD_UNIT_PREFIX`)

Имя systemd-сервиса формируется как `{префикс}@{имя конфига без расширения}`.

Примеры для `OPENVPN_CONFIG_FILE_NAME=server.conf`:

| Префикс | Команда перезапуска |
|---------|---------------------|
| `openvpn-server` | `systemctl restart openvpn-server@server` |
| `openvpn` | `systemctl restart openvpn@server` |

Узнать фактическое имя unit на сервере:

```bash
systemctl list-units 'openvpn*'
```

#### Права sudo для перезапуска

Приложение выполняет `sudo systemctl restart <unit>`. Пользователю, под которым запущен vpndirector, нужен NOPASSWD-доступ. Пример `/etc/sudoers.d/vpndirector` (замените `vpndirector` на пользователя из unit-файла):

```
vpndirector ALL=(root) NOPASSWD: /bin/systemctl restart openvpn-server@server
```

Для другого конфига или префикса скорректируйте имя unit.

### Подготовка OpenVPN

В `/etc/openvpn` необходимо поместить шаблоны клиентских конфигов (есть в репозитории),
предварительно отредактировав шаблоны на предмет IP-адреса сервера, добавить CA сервера и статический ключ сервера.

При автоматическом формировании конфигов производится подстановка клиентского приватного ключа и сертификата в соответствующие разделы; разделы `<key>` и `<cert>` в шаблоне не менять.

Рекомендуется скопировать рабочий конфиг сервера (`/etc/openvpn/server.conf` → `/etc/openvpn/server.conf_default`) — это позволит откатиться на бэкап из веб-интерфейса.

## Запуск
Запуск осуществлять файлом `launch.sh`.

### Установка как сервис (Debian/Ubuntu)

1. Скопировать `vpndirector.service` в `/etc/systemd/system/`, отредактировать путь к `.jar` и пользователя.
2. Включить автозапуск: `systemctl enable vpndirector.service`
3. Запустить: `systemctl start vpndirector.service`
4. Проверить статус: `systemctl status vpndirector.service`

## Настройка nginx

Отредактируйте фрагмент ниже с учётом порта приложения (`SERVER_PORT`).
Если `location` отличен от `/`, задайте `SERVER_CONTEXT_PATH` в `.env`.

```nginx
## VPN Director
server {
    listen 80;
    listen [::]:80;
    return 301 https://$server_name$request_uri;
}
server {
    listen 443 ssl http2;
    ssl_certificate /etc/nginx/ssl/fullchain.pem;
    ssl_certificate_key /etc/nginx/ssl/privkey.pem;

    location / {
        proxy_pass http://127.0.0.1:8080;  # Порт из SERVER_PORT
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        proxy_set_header X-Forwarded-Host $host;
        proxy_set_header X-Forwarded-Port $server_port;

        proxy_connect_timeout 60s;
        proxy_send_timeout 60s;
        proxy_read_timeout 60s;

        proxy_buffering off;
    }
}
```

## Changelog

### [0.0.6] - 2026-08-10

#### Добавлено
- Обработка подозрительных URL (`RequestRejectedException`, в т.ч. с `;`): ответ 400, запись в WARN без ERROR-стека; сервис продолжает работать
- Сессии только через cookie (`server.servlet.session.tracking-modes: cookie`), без `;jsessionid=` в URL

#### Изменено
- На странице входа (до авторизации) вместо «VPN Director» отображается «VD» (заголовок и вкладка браузера)

### [0.0.5] - 2026-05-22

#### Добавлено
- Настройка часового пояса `SERVER_TIMEZONE` (смещение от UTC в часах, по умолчанию `3`)

#### Изменено
- Время подключений и длительность отображаются в соответствии с `SERVER_TIMEZONE`

#### Исправлено
- CSV v3: время подключения в формате `dd.MM.yyyy HH:mm:ss` (не unix time)
- Корректный расчёт длительности по полю Connected Since (time_t)

### [0.0.4] - 2026-05-22

#### Добавлено
- Автоопределение формата файла статуса OpenVPN (legacy и CSV v3 для OpenVPN 2.5+)
- Настройка префикса systemd unit: `OPENVPN_SYSTEMD_UNIT_PREFIX` (по умолчанию `openvpn-server`)
- Таблица переменных окружения и разделы документации по status-файлу, перезапуску и sudo

#### Изменено
- Перезапуск формирует имя сервиса как `{префикс}@{конфиг}` (например, `openvpn-server@server`)
- При ошибке перезапуска в интерфейс выводится вывод `systemctl`

#### Исправлено
- Просмотр активных подключений для status-файлов OpenVPN 2.6 (формат `CLIENT_LIST,...`)
- Перезапуск на системах с unit `openvpn-server@`, а не `openvpn@`

### [0.0.3] - 2025-12-25

#### Добавлено
- Настройка пути к файлу статуса OpenVPN через `OPENVPN_STATUS_FILE_NAME`

#### Изменено
- Имя инстанса при перезапуске берётся из `OPENVPN_CONFIG_FILE_NAME` (без расширения)

### [0.0.2] — начальная версия

- Редактирование конфига, маршруты, сертификаты, клиентские конфиги, активные соединения, перезапуск сервиса

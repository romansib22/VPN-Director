# VPN-Director
Система управления OpenVpn сервером
Позволяет редактировать конфиг OpenVpn-сервера, добавлять маршруты для подсетей и для отдельных IP по доменному имени.
Есть возможность выпускать сертификаты, просматривать уже выпущенные и генерировать конфигурационные файлы клиентов
Просмотр активных соединений
Перезапуск сервиса

## Требования
Java 11

## Настройка
Настройки приложения хранятся в .env -файле, который должен лежать рядом с .jar -файлом.
Необходимо скопировать .env_example в .env и отредактировать его в соответствии со своим окружением.
В /etc/openvpn необходимо поместить шаблоны клиентских конфигов (есть в репозитарии)
предварительно отредактировать шаблоны на предмет ip-адреса сервера
добавить ca-сервера и статический ключ
При автоматическом формировании конфигов производится подстановка клиентского приватного ключа и сертификата, в соответствующие разделы, таким образом, разделы <key> и <cert> не трогать, оставить как они есть.
Также рекомендуется скопировать рабочий конфиг сервера (/etc/openvpn/server.conf в /etc/openvpn/server.conf_default), это позволит откатиться на этот бэкап из веб-интерфейса, если что-то пойдёт не так.

## Запуск
Запуск осущетвлять файлом launch.sh

#Установка как сервис в deb-системы:
1. скопировать vpndirector.service в /etc/systemd/system/
2. включить автозапуск: systemctl enable vpndirector.service
3. запустить: systemctl start vpndirector.service
4. проверить статус: systemctl status vpndirector.service

## Настройка nginx

Отредактируйте данный форагмент, исходя из порта приложения.
Если location отличен от /, то необходимо сделать соответствующую настройку в .env-файле (параметр SERVER_CONTEXT_PATH)

    ##VPN Director
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
                proxy_pass http://127.0.0.1:8080;  # Порт из application.yml (SERVER_PORT)
                proxy_set_header Host $host;
                proxy_set_header X-Real-IP $remote_addr;
                proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
                proxy_set_header X-Forwarded-Proto $scheme;

                # Важно для Spring Security и сессий
                proxy_set_header X-Forwarded-Host $host;
                proxy_set_header X-Forwarded-Port $server_port;

                # Таймауты
                proxy_connect_timeout 60s;
                proxy_send_timeout 60s;
                proxy_read_timeout 60s;

                # Отключение буферизации для SSE (если используется)
                proxy_buffering off;
        }
    }

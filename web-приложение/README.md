# Помощник куратора — веб-приложение

Мобильное веб-приложение (PWA) с тем же интерфейсом и Firebase-базой, что и Android-версия.

## Возможности

- Вход и регистрация (Firebase Auth)
- Главное меню с карточками по роли (учащийся, староста, куратор)
- Мои отметки
- Пропуски
- Расписание
- Уведомления
- Групповой чат
- Профиль и выход

Разделы «Питание», «Кружки», «Кабинет куратора» и др. — заглушки (полный функционал в Android).

## Быстрый старт (локально)

1. **Firebase Console** → проект `assistant-curator` → ⚙️ Project settings → **Add app** → **Web** (</>)
2. Скопируйте `appId` в файл `js/config.js` → поле `appId`
3. **Authentication** → **Settings** → **Authorized domains** → добавьте `localhost`
4. Запустите локальный сервер (Firebase не работает с `file://`):

```bash
cd web-приложение
npx --yes serve .
```

5. Откройте http://localhost:3000 и войдите тем же email/паролем, что в Android-приложении.

## Размещение на Beget

1. Создайте сайт или поддомен в панели Beget
2. Включите **SSL (HTTPS)** — обязательно для Firebase Auth
3. Загрузите **все файлы** из папки `web-приложение` в `public_html` (или подпапку)
4. В Firebase Console → Authorized domains → добавьте ваш домен (`ваш-сайт.ru`)
5. Обновите `appId` в `js/config.js` если ещё не сделали

## Структура

```
web-приложение/
  index.html          — точка входа
  manifest.json       — PWA (иконки добавьте в icons/)
  css/styles.css      — стили как в Android (тёмная тема, фиолетовый акцент)
  js/
    config.js         — настройки Firebase
    db.js             — работа с Firestore
    views.js          — шаблоны экранов
    app.js            — маршрутизация
```

## Цвета (как в Android)

| Элемент | Цвет |
|---------|------|
| Фон | `#020617` → `#1E1B4B` |
| Карточки | `#1E293B` |
| Акцент | `#8B5CF6` |
| Текст | `#F8FAFC` |

## Тестовые пользователи

Те же, что для Android (пароль `12345678`):

- Куратор: `debeselena001@gmail.com`
- Староста: `belushdaria001@gmail.com`

## Push-уведомления (опционально)

Для Web Push нужно дополнительно:

1. Firebase → Cloud Messaging → Web Push certificates
2. Service Worker `firebase-messaging-sw.js`
3. Разрешение браузера на уведомления

Базовая версия работает без push — уведомления видны в разделе «Уведомления».

## Иконки PWA

Положите в папку `icons/`:

- `icon-192.png` (192×192)
- `icon-512.png` (512×512)

Можно экспортировать из `app/src/main/ic_launcher-playstore.png`.

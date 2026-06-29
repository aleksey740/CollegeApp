/* UI-шаблоны — как экраны Android */

const UI = {
  login(err = '') {
    return `<div class="auth-page auth-page--login">
      <div class="auth-title">Помощник куратора</div>
      <div class="auth-subtitle">Добро пожаловать!</div>
      <div class="auth-card">
        <h2 class="auth-card-title">Вход</h2>
        ${err ? `<p class="form-error">${escapeHtml(err)}</p>` : ''}
        <label class="form-label" for="email">Email</label>
        <input class="form-input auth-input" id="email" type="email" placeholder="example@email.com" autocomplete="email">
        <span class="field-error" id="emailError"></span>
        <label class="form-label" for="password">Пароль</label>
        <div class="password-wrap">
          <input class="form-input auth-input" id="password" type="password" placeholder="••••••••" autocomplete="current-password">
          <button type="button" class="password-toggle" aria-label="Показать пароль">👁</button>
        </div>
        <span class="field-error" id="passwordError"></span>
        <button class="btn-primary" id="loginBtn" type="button">Войти</button>
        <div class="spinner" id="loginSpinner"></div>
      </div>
      <div class="auth-footer">Нет аккаунта? <a data-route="register">Создать</a></div>
      <p class="auth-consent auth-consent--login">Входя в приложение, вы соглашаетесь с <a data-route="privacy">Политикой конфиденциальности</a>.</p>
    </div>`;
  },

  register() {
    return `<div class="auth-page auth-page--register">
      <div class="auth-title">Регистрация</div>
      <div class="auth-subtitle">Создайте новый аккаунт</div>
      <div class="auth-card auth-card--register">
        <label class="form-label" for="fullName">ФИО</label>
        <input class="form-input auth-input auth-input--sm" id="fullName" type="text" placeholder="Иванов Иван Иванович" autocomplete="name">
        <span class="field-error" id="fullNameError"></span>
        <label class="form-label" for="email">Email</label>
        <input class="form-input auth-input auth-input--sm" id="email" type="email" placeholder="example@email.com" autocomplete="email">
        <span class="field-error" id="emailError"></span>
        <label class="form-label" for="password">Пароль</label>
        <div class="password-wrap">
          <input class="form-input auth-input auth-input--sm" id="password" type="password" placeholder="Минимум 6 символов" autocomplete="new-password">
          <button type="button" class="password-toggle" aria-label="Показать пароль">👁</button>
        </div>
        <span class="field-error" id="passwordError"></span>
        <label class="form-label" for="gender">Пол</label>
        <select class="form-select auth-input auth-input--sm" id="gender">
          <option value="male">Мужской</option>
          <option value="female">Женский</option>
        </select>
        <span class="field-error" id="genderError"></span>
        <label class="form-label" for="groupName">Группа</label>
        <input class="form-input auth-input auth-input--sm" id="groupName" type="text" placeholder="Например: ИТ-21" autocomplete="off">
        <span class="field-error" id="groupError"></span>
        <label class="form-label" for="role">Роль</label>
        <select class="form-select auth-input auth-input--sm" id="role">
          <option value="student">Учащийся</option>
          <option value="headman">Староста</option>
          <option value="teacher">Куратор</option>
        </select>
        <p class="auth-consent auth-consent--register">Продолжая, вы соглашаетесь с <a data-route="privacy">Политикой конфиденциальности</a>.</p>
        <button class="btn-primary" id="registerBtn" type="button">Создать аккаунт</button>
        <div class="spinner" id="registerSpinner"></div>
      </div>
      <div class="auth-footer">Уже есть аккаунт? <a data-route="login">Войти</a></div>
    </div>`;
  },

  privacy() {
    return `<div class="page-scroll main-screen">
      ${UI.screenHeader('Политика конфиденциальности', '📄', 'purple menu-icon', 'purple', { subtitle: 'Документ о защите данных' })}
      <div class="page-content legal-text">${getPrivacyPolicyHtml()}</div>
    </div>`;
  },

  pageHeader(title, subtitle, icon, iconClass, headerClass = '') {
    return UI.screenHeader(title, icon, iconClass, headerClass || 'default', { subtitle });
  },

  screenHeader(title, icon, iconClass, theme = 'default', opts = {}) {
    const exportBtn = opts.exportId
      ? `<button type="button" class="header-export-btn" id="${opts.exportId}" title="Экспорт">💾</button>` : '';
    return `<div class="page-header page-header--${theme}">
      <button class="back-btn" data-route="back" aria-label="Назад">‹</button>
      <div class="page-header-icon ${iconClass} menu-icon">${icon}</div>
      <div class="page-header-text"><h1>${escapeHtml(title)}</h1>
        ${opts.subtitle ? `<p>${escapeHtml(opts.subtitle)}</p>` : ''}</div>
      <div class="header-actions" id="headerActions">${exportBtn}${opts.actions || ''}</div>
    </div>`;
  },

  gradesStatsCard(avg, count) {
    const avgNum = avg === '—' ? 0 : parseFloat(avg) || 0;
    return `<div class="stats-hero-card">
      <div class="stats-hero-col">
        <div class="stats-hero-emoji">⭐</div>
        <div class="stats-hero-value" style="color:${avgGradeColor(avgNum)}">${escapeHtml(String(avg))}</div>
        <div class="stats-hero-label">Средний балл</div>
      </div>
      <div class="stats-hero-divider"></div>
      <div class="stats-hero-col">
        <div class="stats-hero-emoji">📝</div>
        <div class="stats-hero-value stat-purple">${count}</div>
        <div class="stats-hero-label">Всего отметок</div>
      </div>
    </div>`;
  },

  gradesFilters(subOpts, semOpts) {
    return `<div class="filter-card">
      <label class="form-label">📚 Предмет</label>
      <select class="form-select filter-select" id="fSubject">${subOpts}</select>
      <label class="form-label">📘 Семестр</label>
      <select class="form-select filter-select" id="fSemester">${semOpts}</select>
    </div>`;
  },

  hubCardHalf(route, icon, cls, title, sub, extra = '') {
    return `<div class="menu-card" data-route="${route}" ${extra}>
      <div class="menu-icon ${cls}">${icon}</div>
      <div class="menu-card-title">${title}</div>
      <div class="menu-card-sub">${sub}</div>
    </div>`;
  },

  hubCardFull(route, icon, cls, title, sub, opts = {}) {
    const xl = opts.xl ? ' xl' : ' lg';
    const extra = opts.requiresGroup ? 'data-requires-group="1"' : '';
    return `<div class="menu-card full" data-route="${route}" ${extra}>
      <div class="menu-icon${xl} ${cls}">${icon}</div>
      <div><div class="menu-card-title">${title}</div><div class="menu-card-sub">${sub}</div></div>
      ${opts.noArrow ? '' : `<span class="menu-arrow${opts.arrow ? ' ' + opts.arrow : ''}">›</span>`}
    </div>`;
  },

  teacherHub(user) {
    return `<div class="page-scroll main-screen">
      <div class="hub-header hub-header--teacher">
        <button class="back-btn hub-back" data-route="back" aria-label="Назад">‹</button>
        <div class="hub-hero">
          <div class="hub-hero-icon hub-hero-icon--blue">👨‍🏫</div>
          <div class="hub-hero-title">${escapeHtml(hubTitle(user, 'Кабинет куратора'))}</div>
          <div class="hub-hero-email">${escapeHtml(user.email || '')}</div>
          <span class="hub-role-badge hub-role-badge--blue">${roleBadgeLabel(user)}</span>
        </div>
      </div>
      <div class="menu-title">Основной функционал</div>
      <div class="menu-grid">
        <div class="menu-row">
          ${UI.hubCardHalf('journal', '📝', 'green', 'Отметки', 'Выставить отметки', 'data-requires-group="1"')}
          ${UI.hubCardHalf('students', '👥', 'blue', 'Моя группа', 'Список группы', 'data-requires-group="1"')}
        </div>
        <div class="menu-row">
          ${UI.hubCardHalf('manage-absences', '📋', 'orange', 'Посещаемость', 'Учёт пропусков')}
          ${UI.hubCardHalf('statistics', '📊', 'purple', 'Статистика', 'Отчёты и аналитика')}
        </div>
        ${UI.hubCardFull('schedule', '📅', 'blue', 'Расписание', 'Пары и учебные дни')}
        ${UI.hubCardFull('events', '🎉', 'purple', 'Мероприятия', 'События группы и напоминания')}
        ${UI.hubCardFull('curatorial', '⏰', 'orange', 'Кураторские часы', 'Информационные часы в расписании', { requiresGroup: true })}
        ${UI.hubCardFull('clubs', '🎯', 'blue', 'Кружки и секции', 'Управление кружками и участниками')}
        ${UI.hubCardFull('nutrition', '🍽️', 'green', 'Питание группы', 'Просмотр записей о питании', { noArrow: true })}
      </div>
    </div>`;
  },

  headmanHub(user) {
    const gn = (user.groupName || user.group || '').trim();
    return `<div class="page-scroll main-screen">
      <div class="hub-header hub-header--headman">
        <button class="back-btn hub-back" data-route="back" aria-label="Назад">‹</button>
        <div class="hub-hero">
          <div class="hub-hero-icon hub-hero-icon--purple">⭐</div>
          <div class="hub-hero-title">${escapeHtml(hubTitle(user, 'Кабинет старосты'))}</div>
          <div class="hub-hero-email">${escapeHtml(user.email || '')}</div>
          <div class="badges" style="justify-content:center;margin-top:16px">
            <span class="hub-role-badge hub-role-badge--purple">${roleBadgeLabel(user)}</span>
            ${gn ? `<span class="badge-group">${escapeHtml(gn)}</span>` : ''}
          </div>
        </div>
      </div>
      <div class="menu-title">Управление группой</div>
      <div class="menu-grid">
        ${UI.hubCardFull('manage-absences', '📋', 'orange', 'Посещаемость', 'Отметка пропусков')}
        ${UI.hubCardFull('schedule', '📅', 'blue', 'Расписание', 'Управление занятиями')}
        ${UI.hubCardFull('events', '🎉', 'purple', 'Мероприятия', 'События вашей группы')}
        ${UI.hubCardFull('statistics', '📊', 'purple', 'Статистика группы', 'Успеваемость и экспорт')}
        ${UI.hubCardFull('nutrition', '🍽️', 'green', 'Питание', 'Запись и анализ по группе', { noArrow: true })}
      </div>
    </div>`;
  },

  adminHub(accessDenied) {
    return `<div class="page-scroll main-screen">
      <div class="hub-header hub-header--admin">
        <button class="back-btn hub-back" data-route="back" aria-label="Назад">‹</button>
        <div class="hub-header-row">
          <div class="menu-icon lg orange">🛡️</div>
          <div>
            <div class="hub-inline-title">Администрирование группы</div>
            ${accessDenied ? '<div class="hub-inline-sub">Доступ только для роли Куратор</div>' : ''}
          </div>
        </div>
      </div>
      <div class="menu-grid" style="padding-top:16px">
        <div class="menu-card full admin-hub-card ${accessDenied ? 'disabled' : ''}" data-route="admin-catalog">
          <div class="menu-icon green">📚</div>
          <div><div class="menu-card-title">Учебный справочник группы</div>
            <div class="menu-card-sub">Предметы, семестры и преподаватели</div></div>
        </div>
        <div class="menu-card full admin-hub-card ${accessDenied ? 'disabled' : ''}" data-route="admin-club-leaders">
          <div class="menu-icon purple">🎯</div>
          <div><div class="menu-card-title">Руководители кружков, секций и факультативов</div></div>
        </div>
      </div>
    </div>`;
  },

  hubPage(title, subtitle, cards) {
    return UI.teacherHub({ fullName: title, email: subtitle, role: 'teacher' });
  },

  main(user, stats, unread) {
    const vis = mainMenuVisibility(user);
    const displayName = (user.fullName || '').trim() || (user.email || '').split('@')[0] || 'Пользователь';
    const avgNum = stats.avg === '—' ? 0 : parseFloat(stats.avg) || 0;

    const cardHalf = (route, icon, cls, title, sub) =>
      `<div class="menu-card" data-route="${route}">
        <div class="menu-icon ${cls}">${icon}</div>
        <div class="menu-card-title">${title}</div>
        <div class="menu-card-sub">${sub}</div>
      </div>`;

    const cardFull = (route, icon, cls, title, sub, opts = {}) => {
      const xl = opts.xl ? ' xl' : ' lg';
      const arrow = opts.badge
        ? `<span class="notif-badge">${opts.badge}</span>`
        : `<span class="menu-arrow${opts.arrow ? ' ' + opts.arrow : ''}">›</span>`;
      return `<div class="menu-card full" data-route="${route}">
        <div class="menu-icon${xl} ${cls}">${icon}</div>
        <div><div class="menu-card-title">${title}</div><div class="menu-card-sub">${sub}</div></div>
        ${arrow}
      </div>`;
    };

    let menu = '';
    if (vis.grades || vis.schedule) {
      menu += '<div class="menu-row">';
      if (vis.grades) menu += cardHalf('grades', '📊', 'green', 'Отметки', 'Успеваемость');
      if (vis.schedule) menu += cardHalf('schedule', '📚', 'blue', 'Расписание', 'Занятия');
      menu += '</div>';
    }
    if (vis.events) menu += cardFull('events', '🎉', 'purple', 'Мероприятия', 'События группы');
    if (vis.absences) menu += cardFull('absences', '📋', 'orange', 'Пропуски', 'Посещаемость');
    menu += cardFull('notifications', '🔔', 'blue', 'Уведомления',
      unread > 0 ? `Непрочитанных: ${unread}` : 'Нет новых уведомлений',
      { badge: unread > 0 ? (unread > 99 ? '99+' : String(unread)) : null });
    menu += cardFull('chat', '💬', 'blue', 'Чат', 'Коммуникации');
    if (vis.nutrition) menu += cardFull('nutrition', '🍽️', 'green', 'Питание', 'Запись и аналитика');
    if (vis.clubs) menu += cardFull('clubs', '🎯', 'purple', 'Кружки и секции', 'Запись и участие');
    if (vis.teacher) menu += cardFull('teacher', '👨‍🏫', 'blue', 'Кабинет куратора', 'Отметки, группы, статистика', { xl: true, arrow: 'accent' });
    if (vis.headman) menu += cardFull('headman', '⭐', 'purple', 'Кабинет старосты', 'Посещаемость, статистика', { xl: true, arrow: 'accent' });
    if (vis.admin) menu += cardFull('admin', '🛡️', 'orange', 'Администрирование группы', 'Справочники группы, лимиты и кружки', { xl: true, arrow: 'accent-amber' });
    menu += cardFull('profile', '👤', 'gray', 'Мой профиль', 'Настройки и аккаунт', { xl: true });

    return `<div class="page-scroll main-screen">
      <div class="main-header">
        <div class="greeting">${getGreeting()}</div>
        <div class="user-name">${escapeHtml(displayName)}</div>
        <div class="badges">
          <span class="badge-role" style="background:${roleBadgeColor(user)}">${roleBadgeLabel(user)}</span>
          ${vis.groupBadge ? `<span class="badge-group">${escapeHtml(user.groupName || user.group)}</span>` : ''}
        </div>
        <div class="date-line">📅 ${formatDateRu(new Date()).replace(/^./, c => c.toUpperCase())}</div>
      </div>
      ${vis.quickStats ? `<div class="quick-stats">
        <div class="stat-card">
          <div class="stat-value" style="color:${avgGradeColor(avgNum)}">${escapeHtml(String(stats.avg))}</div>
          <div class="stat-label">Ср. балл</div>
        </div>
        <div class="stat-card">
          <div class="stat-value stat-purple">${stats.gradesCount}</div>
          <div class="stat-label">Отметок</div>
        </div>
        <div class="stat-card">
          <div class="stat-value stat-amber">${stats.absenceHours} ч.</div>
          <div class="stat-label">Пропусков</div>
        </div>
      </div>` : ''}
      <div class="menu-title">Меню</div>
      <div class="menu-grid">${menu}</div>
    </div>`;
  },

  gradesList(list) {
    if (!list.length) return `<div class="empty-state"><span>📊</span>Отметок пока нет</div>`;
    return list.map(g => `<div class="grade-item" data-grade-id="${g.id}">
      <div class="grade-info">
        <div class="grade-subject">${escapeHtml(g.subject)}</div>
        ${g.type ? `<div class="grade-type">${escapeHtml(g.type)}</div>` : ''}
        ${g.semester ? `<span class="grade-sem-chip">${g.semester} семестр</span>` : ''}
      </div>
      <div class="grade-right">
        <div class="grade-circle ${gradeColorClass(g.value)}">${g.value}</div>
        <div class="grade-date">${escapeHtml(g.date)}</div>
      </div></div>`).join('');
  },

  absencesList(list, showStudent = false) {
    if (!list.length) return `<div class="empty-state"><span>📋</span>Пропусков нет</div>`;
    return list.map(a => `<div class="list-card" data-absence-id="${a.id}">
      ${showStudent ? `<div class="list-card-title">${escapeHtml(a.studentName)}</div>` : `<div class="list-card-title">${escapeHtml(a.subject || 'Занятие')}</div>`}
      <div class="list-card-body">${a.hours} ч.${(a.isExcused ?? a.excused) ? ' · уваж.' : ''}${a.reason ? ' · ' + escapeHtml(absenceReasonLabel(a.reason)) : ''}${a.comment ? ' · ' + escapeHtml(a.comment) : ''}</div>
      <div class="list-card-meta">${escapeHtml(a.date)}${showStudent && a.subject ? ' · ' + escapeHtml(a.subject) : ''}</div></div>`).join('');
  },

  scheduleList(items, opts = {}) {
    if (!items.length) return `<div class="empty-state"><span>📚</span>Расписание не найдено</div>`;
    let html = '', lastDay = '';
    items.forEach(item => {
      if (item.day !== lastDay && !opts.singleDay) {
        lastDay = item.day;
        html += `<div class="schedule-day-title">${escapeHtml(item.day)}</div>`;
      }
      const typeLabel = scheduleTypeLabel(item.type);
      const typeColor = scheduleTypeColor(item.type);
      const teachers = formatTeachersLine(item);
      const room = formatRoomLine(item);
      const subgroup = item.isSubgroup ? '<span class="schedule-badge">Подгруппы</span>' : '';
      html += `<div class="schedule-item" data-schedule-id="${item.id}">
        <div class="schedule-type-bar" style="background:${typeColor}"></div>
        <div class="schedule-time">${escapeHtml(item.time)}</div>
        <div class="schedule-main">
          <div class="schedule-subject">${escapeHtml(item.type === 'LUNCH' ? 'Обед' : item.subject)} ${subgroup}</div>
          ${teachers ? `<div class="schedule-detail">${teachers}</div>` : ''}
          ${room ? `<div class="schedule-detail">${room}</div>` : ''}
          <div class="schedule-meta">
            <span class="schedule-type-chip" style="background:${typeColor}">${escapeHtml(typeLabel)}</span>
            ${item.date ? `<span>• ${escapeHtml(item.date)}</span>` : ''}
          </div>
        </div></div>`;
    });
    return html;
  },

  curatorialHoursList(items, studentsMap = {}) {
    if (!items.length) return `<div class="empty-state"><span>🕐</span>Часов пока нет</div>`;
    return items.map(item => {
      const isCur = item.type === 'CURATOR_HOUR';
      const color = isCur ? scheduleTypeColor('CURATOR_HOUR') : scheduleTypeColor('INFO_HOUR');
      const badge = isCur ? 'Кураторский час' : 'Информационный час';
      const assigned = (item.assignedStudentIds || []).map(id => studentsMap[id]).filter(Boolean);
      const assignedHtml = !isCur && assigned.length
        ? `<div class="list-card-meta">👨‍🎓 ${assigned.map(s => escapeHtml(s.fullName)).join(', ')}</div>` : '';
      return `<div class="list-card clickable curatorial-item" data-schedule-id="${item.id}">
        <div class="curatorial-item-head">
          <span class="schedule-type-chip" style="background:${color}">${badge}</span>
          <span class="list-card-meta">📅 ${escapeHtml(item.date)} • ${escapeHtml(item.time)}</span>
        </div>
        <div class="list-card-title">${escapeHtml(item.subject || '—')}</div>
        ${item.room ? `<div class="list-card-body">🏫 ${escapeHtml(item.room)}</div>` : ''}
        ${assignedHtml}
      </div>`;
    }).join('');
  },

  eventsList(events, canEdit) {
    if (!events.length) return `<div class="empty-state"><span>🎉</span>Мероприятий нет</div>`;
    return events.map(e => `<div class="list-card" data-event-id="${e.id}">
      <div class="list-card-title">${escapeHtml(e.title)}</div>
      <div class="list-card-body">${escapeHtml(e.date)} ${escapeHtml(e.time)}${e.place ? ' · ' + escapeHtml(e.place) : ''}</div>
      ${e.description ? `<div class="list-card-meta">${escapeHtml(e.description)}</div>` : ''}
      ${canEdit && canModify(window.__currentUser, e.createdBy, e.createdByRole) ? `<div class="card-actions"><button class="btn-sm" data-edit-event="${e.id}">Изменить</button><button class="btn-sm btn-danger" data-del-event="${e.id}">Удалить</button></div>` : ''}
    </div>`).join('');
  },

  notificationsList(list) {
    if (!list.length) return `<div class="empty-state"><span>🔔</span>Уведомлений нет</div>`;
    return list.map(n => `<div class="list-card ${n.isRead ? 'read' : 'unread'}" data-notif-id="${n.id}">
      <div class="list-card-title">${escapeHtml(n.title)}</div>
      <div class="list-card-body">${escapeHtml(n.message)}</div>
      <div class="list-card-meta">${formatTime(n.createdAt)} · ${n.createdAt.toLocaleDateString('ru-RU')}</div></div>`).join('');
  },

  profile(user, stats) {
    const showLearner = isStudent(user) || isHeadman(user);
    const gn = (user.groupName || user.group || '').trim();
    const showGroup = user.role !== 'teacher' && user.role !== 'admin' && !!gn;
    const hasPersonal = !!(user.address || user.birthDate || user.phone);
    const avgNum = stats && stats.avg !== '—' ? parseFloat(stats.avg) || 0 : 0;
    const initial = (displayName(user)[0] || '?').toUpperCase();

    const settingsItem = (route, icon, cls, title, sub, id) => `
      <div class="settings-item" ${route ? `data-route="${route}"` : ''} ${id ? `id="${id}"` : ''}>
        <div class="menu-icon ${cls}">${icon}</div>
        <div class="settings-item-text"><div class="settings-item-title">${title}</div>
          ${sub ? `<div class="settings-item-sub">${sub}</div>` : ''}</div>
        <span class="menu-arrow">›</span>
      </div>`;

    return `<div class="page-scroll main-screen profile-page">
      <div class="hub-header hub-header--profile">
        <button class="back-btn hub-back" data-route="back" aria-label="Назад">‹</button>
        <div class="hub-hero">
          <div class="profile-avatar-lg" style="background:${profileAvatarColor(user)}">${escapeHtml(initial)}</div>
          <div class="hub-hero-title">${escapeHtml(displayName(user))}</div>
          <div class="hub-hero-email">${escapeHtml(user.email || '')}</div>
          <div class="badges" style="justify-content:center;margin-top:16px">
            <span class="badge-role" style="background:${roleBadgeColor(user)}">${roleBadgeLabel(user)}</span>
            ${showGroup ? `<span class="badge-group">${escapeHtml(gn)}</span>` : ''}
          </div>
        </div>
      </div>
      ${showLearner && stats ? `<div class="quick-stats profile-stats">
        <div class="stat-card">
          <div class="stat-value" style="color:${avgGradeColor(avgNum)}">${escapeHtml(String(stats.avg))}</div>
          <div class="stat-label">Ср. балл</div>
        </div>
        <div class="stat-card">
          <div class="stat-value stat-purple">${stats.gradesCount}</div>
          <div class="stat-label">Отметок</div>
        </div>
        <div class="stat-card">
          <div class="stat-value stat-amber">${stats.absenceHours} ч.</div>
          <div class="stat-label">Пропусков</div>
        </div>
      </div>` : ''}
      ${showLearner && hasPersonal ? `<div class="profile-info-card">
        <div class="profile-info-title">📋 Личная информация</div>
        <div class="profile-info-row"><span>📍 Адрес:</span><span>${escapeHtml(user.address || 'Не указано')}</span></div>
        <div class="profile-info-row"><span>🎂 Дата рождения:</span><span>${escapeHtml(user.birthDate || 'Не указано')}</span></div>
        <div class="profile-info-row"><span>📱 Телефон:</span><span>${escapeHtml(user.phone || 'Не указано')}</span></div>
      </div>` : ''}
      <div class="settings-title">⚙️ Настройки</div>
      <div class="settings-card">
        ${showLearner ? settingsItem('edit-profile', '📝', 'blue', 'Личная информация', 'Заполнить или изменить') + '<div class="settings-divider"></div>' : ''}
        ${settingsItem('', '🔐', 'purple', 'Сменить пароль', 'Изменить пароль аккаунта', 'resetPwdBtn')}
        <div class="settings-divider"></div>
        ${settingsItem('privacy', '📄', 'gray', 'Политика конфиденциальности', '')}
        <div class="settings-divider"></div>
        ${settingsItem('', '🚪', 'red', '<span class="text-danger">Выйти из аккаунта</span>', 'Завершить текущую сессию', 'logoutBtn')}
      </div>
      <div class="profile-version">Помощник куратора v1.0</div>
    </div>`;
  },

  filterBar(fields) {
    return `<div class="filter-bar">${fields.map(f =>
      f.type === 'select' ? `<select class="filter-select" id="${f.id}">${f.options.map(o =>
        `<option value="${o.v}">${escapeHtml(o.l)}</option>`).join('')}</select>`
        : `<input class="filter-input" id="${f.id}" type="${f.type||'text'}" placeholder="${escapeHtml(f.ph||'')}">`
    ).join('')}</div>`;
  },

  adminCatalog(catalog, selectedTeacherId) {
    const { groupName, groupId, subjects, semesters, teachers, limits, stats } = catalog;
    const selId = selectedTeacherId || teachers[0]?.id || '';
    const selected = teachers.find(t => t.id === selId);
    const subjectIdToName = Object.fromEntries(
      (subjects || []).flatMap(s => {
        const gid = s.groupId || groupId;
        const entries = [[subjectDocumentId(s.name, gid), s.name]];
        if (s.id) entries.push([s.id, s.name]);
        return entries;
      })
    );
    const subjectRows = (subjects || []).length
      ? subjects.map(s => `<div class="catalog-row">
          <div class="catalog-row-main">
            <div class="catalog-row-title">${escapeHtml(s.name)}</div>
            <div class="catalog-row-sub">${escapeHtml(semestersDisplayText(s, semesters))}</div>
          </div>
          <div class="card-actions">
            <button type="button" class="btn-sm" data-edit-subject="${escapeHtml(s.name)}">Изменить</button>
            <button type="button" class="btn-sm btn-danger-outline" data-rm-subject="${escapeHtml(s.name)}">Удалить</button>
          </div></div>`).join('')
      : '<p class="muted catalog-empty">Пока нет предметов — добавьте предмет выше</p>';
    const semesterRows = (semesters || []).length
      ? semesters.map(s => `<div class="catalog-row">
          <div class="catalog-row-main">
            <div class="catalog-row-title">${escapeHtml(s.name)}</div>
            <div class="catalog-row-sub">${escapeHtml(s.startDate)} — ${escapeHtml(s.endDate)}</div>
          </div>
          <div class="card-actions">
            <button type="button" class="btn-sm" data-edit-semester="${s.id}">Изменить</button>
            <button type="button" class="btn-sm btn-danger-outline" data-rm-semester="${s.id}">Удалить</button>
          </div></div>`).join('')
      : '<p class="muted catalog-empty">Семестры не заведены — заполните форму выше</p>';
    const teacherRows = (teachers || []).length
      ? teachers.map(t => `<div class="catalog-row">
          <div class="catalog-row-main">
            <div class="catalog-row-title">${escapeHtml(t.fullName)}</div>
          </div>
          <div class="card-actions">
            <button type="button" class="btn-sm" data-edit-teacher="${t.id}">Изменить</button>
            <button type="button" class="btn-sm btn-danger-outline" data-rm-teacher="${t.id}">Удалить</button>
          </div></div>`).join('')
      : '<p class="muted catalog-empty">Справочник пуст — добавьте ФИО преподавателя</p>';
    const bindingRows = selected && (selected.subjectIds || []).length
      ? selected.subjectIds.map(sid => {
          const label = subjectIdToName[sid] || sid;
          return `<div class="catalog-binding-row">
            <span>${escapeHtml(label)}</span>
            <button type="button" class="btn-sm btn-danger-outline" data-unbind-teacher="${selected.id}" data-unbind-subject="${escapeHtml(sid)}">Удалить</button>
          </div>`;
        }).join('')
      : `<p class="muted catalog-empty">${selected ? 'У этого преподавателя пока нет назначенных предметов' : 'Выберите преподавателя в списке выше'}</p>`;
    const subAssignOpts = (subjects || []).map(s =>
      `<option value="${escapeHtml(s.name)}">${escapeHtml(s.name)}</option>`).join('');
    const semAssignOpts = (semesters || []).map(s =>
      `<option value="${s.id}">${escapeHtml(semesterLabel(s))}</option>`).join('');
    const teacherSelectOpts = (teachers || []).map(t =>
      `<option value="${t.id}" ${t.id === selId ? 'selected' : ''}>${escapeHtml(t.fullName)}</option>`).join('');
    const teacherSubOpts = (subjects || []).map(s =>
      `<option value="${escapeHtml(s.name)}">${escapeHtml(s.name)}</option>`).join('');

    const badge = (n) => `<span class="catalog-badge">${n}</span>`;

    return `<div class="page-scroll main-screen">
      ${UI.screenHeader('Учебный справочник группы', '📚', 'orange menu-icon', 'orange', { subtitle: groupName })}
      <div class="page-content catalog-page">
        <div class="catalog-card">
          <div class="catalog-card-head"><span class="catalog-card-icon blue">👥</span>
            <div><div class="catalog-card-title">Лимиты на группу</div>
            <div class="catalog-card-sub">Пустое поле — без ограничения по показателю</div></div>
          </div>
          <label class="form-label">Лимит кураторов</label>
          <input class="form-input" id="limTeachers" type="number" min="0" value="${limits?.teacherLimit ?? ''}">
          <label class="form-label">Лимит учащихся (старосты входят)</label>
          <input class="form-input" id="limStudents" type="number" min="0" value="${limits?.studentLimit ?? ''}">
          <label class="form-label">Лимит старост</label>
          <input class="form-input" id="limHeadmen" type="number" min="0" value="${limits?.headmanLimit ?? ''}">
          <button type="button" class="btn-primary" id="saveLimitsBtn" style="margin-top:12px">Установить лимит</button>
        </div>

        <div class="catalog-card">
          <div class="catalog-card-head"><span class="catalog-card-icon green">📘</span>
            <div><div class="catalog-card-title">Предметы ${badge(stats.subjects)}</div>
            <div class="catalog-card-sub">Предметы и привязка к семестрам</div></div>
          </div>
          <label class="form-label">Группа</label>
          <input class="form-input" id="catalogGroupName" value="${escapeHtml(groupName)}" disabled>
          <label class="form-label">Новый предмет</label>
          <input class="form-input" id="subjectInput" placeholder="Название предмета">
          <button type="button" class="btn-primary" id="addSubjectBtn" style="margin-top:8px">Добавить предмет</button>
          <div class="catalog-divider"></div>
          <div class="catalog-section-title">Связь предмета с семестром</div>
          <p class="catalog-hint">Можно назначить несколько семестров; один семестр — один раз</p>
          <label class="form-label">Предмет</label>
          <select class="form-select" id="assignSubject">${subAssignOpts || '<option value="">—</option>'}</select>
          <label class="form-label">Семестр</label>
          <select class="form-select" id="assignSemester">${semAssignOpts || '<option value="">—</option>'}</select>
          <div class="toolbar" style="margin-top:8px">
            <button type="button" class="btn-sm btn-primary" id="assignSemesterBtn">Назначить семестр</button>
            <button type="button" class="btn-sm btn-danger-outline" id="unassignSemesterBtn">Отвязать семестр</button>
          </div>
          <div class="catalog-list">${subjectRows}</div>
        </div>

        <div class="catalog-card">
          <div class="catalog-card-head"><span class="catalog-card-icon purple">📅</span>
            <div><div class="catalog-card-title">Семестры ${badge(stats.semesters)}</div>
            <div class="catalog-card-sub">Учебные периоды группы</div></div>
          </div>
          <label class="form-label">Группа</label>
          <input class="form-input" value="${escapeHtml(groupName)}" disabled>
          <label class="form-label">Номер семестра</label>
          <input class="form-input" id="semesterNumber" type="number" min="1" placeholder="1">
          <label class="form-label">Дата начала (ДД.ММ.ГГГГ)</label>
          <input class="form-input" id="semesterStart" placeholder="01.09.2025">
          <label class="form-label">Дата конца (ДД.ММ.ГГГГ)</label>
          <input class="form-input" id="semesterEnd" placeholder="31.01.2026">
          <button type="button" class="btn-primary" id="addSemesterBtn" style="margin-top:8px">Добавить семестр</button>
          <div class="catalog-list">${semesterRows}</div>
        </div>

        <div class="catalog-card">
          <div class="catalog-card-head"><span class="catalog-card-icon blue">👨‍🏫</span>
            <div><div class="catalog-card-title">Преподаватели ${badge(stats.teachers)}</div>
            <div class="catalog-card-sub">Справочник и привязки к предметам · ${stats.bindings} привязок</div></div>
          </div>
          <label class="form-label">ФИО преподавателя</label>
          <input class="form-input" id="teacherNameInput" placeholder="Иванов Иван Иванович">
          <button type="button" class="btn-primary" id="addTeacherBtn" style="margin-top:8px">Добавить преподавателя</button>
          <div class="catalog-divider"></div>
          <div class="catalog-section-title">Привязка преподавателя к предмету</div>
          <label class="form-label">Преподаватель</label>
          <select class="form-select" id="teacherSelect">${teacherSelectOpts || '<option value="">—</option>'}</select>
          <label class="form-label">Предмет</label>
          <select class="form-select" id="teacherSubjectSelect">${teacherSubOpts || '<option value="">—</option>'}</select>
          <button type="button" class="btn-sm btn-primary" id="addTeacherBindingBtn" style="margin-top:8px">Добавить привязку</button>
          <div class="catalog-section-title" style="margin-top:16px">Назначенные предметы</div>
          <div id="teacherBindings">${bindingRows}</div>
          <div class="catalog-list" style="margin-top:12px">${teacherRows}</div>
        </div>
      </div></div>`;
  },

  journalGrid(students, dates, gradesMap, labelsMap) {
    if (!students.length) return `<div class="empty-state">Нет учащихся в группе</div>`;
    let header = dates.map(d => {
      const lbl = labelsMap?.[d] || '';
      return `<th class="journal-col-h" data-col-date="${d}" title="${escapeHtml(d)}">${escapeHtml(shortDate(d))}${lbl ? `<div class="col-type">${escapeHtml(lbl)}</div>` : ''}</th>`;
    }).join('');
    let rows = students.map(st => {
      let cells = dates.map(d => {
        const key = `${st.id}_${d}`;
        const g = gradesMap[key];
        return `<td class="journal-cell" data-student="${st.id}" data-date="${d}">${displayGradeCell(g)}</td>`;
      }).join('');
      return `<tr><td class="journal-name">${escapeHtml(st.fullName)}</td>${cells}</tr>`;
    }).join('');
    return `<div class="journal-wrap"><table class="journal-table"><thead><tr><th>Учащийся</th>${header}</tr></thead><tbody>${rows}</tbody></table></div>`;
  },

  journalToolbar(weekLabel, subOpts, semOpts) {
    return `<div class="journal-toolbar">
      <div class="week-nav">
        <button class="btn-sm" id="prevWeek">‹</button>
        <span id="weekLabel" class="week-label">${escapeHtml(weekLabel)}</span>
        <button class="btn-sm" id="nextWeek">›</button>
      </div>
      <label class="form-label">Предмет</label><select class="form-select" id="jSubject">${subOpts || '<option>—</option>'}</select>
      <label class="form-label">Семестр</label><select class="form-select" id="jSemester">${semOpts}</select>
      <button class="btn-sm" id="exportJournal" style="margin-top:8px">Экспорт CSV</button>
    </div>`;
  },

  studentInfo(user) {
    const cats = [];
    if (user.livesInDormitory) cats.push('Общежитие');
    if (user.isDisabled) cats.push('Инвалидность');
    if (user.isLargeFamily) cats.push('Многодетная семья');
    if (user.isLowIncome) cats.push('Малообеспеченная');
    if (user.isOrphan) cats.push('Сирота');
    if (user.isNonResident) cats.push('Иногородний');
    return `<div class="page-scroll">
      ${UI.pageHeader('Учащийся', user.fullName, '👤', 'blue menu-icon', 'blue')}
      <div class="page-content">
        <div class="profile-avatar">${initials(user.fullName)}</div>
        <div class="profile-name">${escapeHtml(user.fullName)}</div>
        <div class="badges" style="justify-content:center;margin:12px 0">
          <span class="badge-role" style="background:${roleBadgeColor(user)}">${roleBadgeLabel(user)}</span>
          ${user.groupName ? `<span class="badge-group">${escapeHtml(user.groupName)}</span>` : ''}
        </div>
        <div class="profile-section">
          <div class="profile-row"><span>Email</span><span>${escapeHtml(user.email)}</span></div>
          ${user.phone ? `<div class="profile-row"><span>Телефон</span><span>${escapeHtml(user.phone)}</span></div>` : ''}
          ${user.birthDate ? `<div class="profile-row"><span>Дата рождения</span><span>${escapeHtml(user.birthDate)}</span></div>` : ''}
          ${user.address ? `<div class="profile-row"><span>Адрес</span><span>${escapeHtml(user.address)}</span></div>` : ''}
          ${user.parentName ? `<div class="profile-row"><span>Родитель</span><span>${escapeHtml(user.parentName)}${user.parentPhone ? ', ' + escapeHtml(user.parentPhone) : ''}</span></div>` : ''}
          ${cats.length ? `<div class="profile-row"><span>Категории</span><span>${cats.map(escapeHtml).join(', ')}</span></div>` : ''}
        </div>
        <button class="btn-primary" data-route="student-grades?id=${encodeURIComponent(user.id)}">Отметки учащегося</button>
      </div></div>`;
  },

  curatorialPage(stats, items, filter, dateFilterLabel, studentsMap = {}) {
    return `<div class="page-scroll">
      ${UI.pageHeader('Кураторские и информационные часы', stats.group, '🕐', 'blue menu-icon', 'blue')}
      <div class="page-content">
        <div class="quick-stats">
          <div class="stat-card"><div class="stat-value stat-purple">${stats.total}</div><div class="stat-label">Всего</div></div>
          <div class="stat-card"><div class="stat-value stat-emerald">${stats.curatorial}</div><div class="stat-label">Кураторские</div></div>
          <div class="stat-card"><div class="stat-value stat-amber">${stats.info}</div><div class="stat-label">Информационные</div></div>
        </div>
        <div class="filter-chips">
          <button class="chip ${filter === 'all' ? 'active' : ''}" data-filter="all">Все</button>
          <button class="chip ${filter === 'CURATOR_HOUR' ? 'active' : ''}" data-filter="CURATOR_HOUR">Кураторские</button>
          <button class="chip ${filter === 'INFO_HOUR' ? 'active' : ''}" data-filter="INFO_HOUR">Информационные</button>
        </div>
        <button class="btn-sm" id="curatorialDateFilter" style="margin-bottom:12px">${escapeHtml(dateFilterLabel || 'Все даты')}</button>
        <div class="toolbar">
          <button class="btn-sm btn-primary" id="addCuratorial">+ Кураторский</button>
          <button class="btn-sm btn-primary" id="addInfo">+ Информационный</button>
        </div>
        ${UI.curatorialHoursList(items, studentsMap)}
      </div></div>`;
  },

  statisticsList(students, statsMap) {
    if (!students.length) return `<div class="empty-state">Нет данных</div>`;
    return students.map(st => {
      const s = statsMap[st.id] || { avg: '—', count: 0 };
      return `<div class="list-card"><div class="list-card-title">${escapeHtml(st.fullName)}</div>
        <div class="list-card-body">Средний балл: ${s.avg} · Отметок: ${s.count}</div></div>`;
    }).join('');
  },

  clubsList(clubs, type) {
    const typeMap = { club: 'CLUB', section: 'SECTION', elective: 'ELECTIVE' };
    const want = typeMap[type] || type.toUpperCase();
    const filtered = clubs.filter(c => (c.type || 'CLUB').toUpperCase() === want);
    if (!filtered.length) return `<div class="empty-state"><span>🎯</span>Нет записей</div>`;
    return filtered.map(c => `<div class="list-card" data-club-id="${c.id}">
      <div class="list-card-title">${escapeHtml(c.name)}</div>
      <div class="list-card-body">${escapeHtml(c.schedule || '')}${c.location ? ' · ' + escapeHtml(c.location) : ''}</div>
      <div class="list-card-meta">Участников: ${(c.participantIds||[]).length}${c.maxParticipants ? '/' + c.maxParticipants : ''}</div>
    </div>`).join('');
  }
};

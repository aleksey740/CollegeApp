/* Помощник куратора — веб-приложение (полная версия) */

let currentUser = null;
let chatUnsub = null;
let roomUnsub = null;
let scheduleUnsub = null;
let notifUnsub = null;
let backStack = ['main'];
const ROUTES = ['main','login','register','privacy','grades','absences','manage-absences','schedule','events',
  'nutrition','clubs','club','notifications','chat','profile','edit-profile','teacher','headman','admin',
  'journal','students','student-info','student-grades','statistics','curatorial','admin-catalog','admin-club-leaders'];

let journalWeekStart = mondayOf(new Date());
let nutritionDate = null;
let curatorialFilter = 'all';
let curatorialDateSingle = null;
let curatorialDateFrom = null;
let curatorialDateTo = null;
let curatorialDateLabel = 'Все даты';

function parseRoute() {
  const raw = location.hash.slice(1) || 'main';
  return raw.split('?')[0] || 'main';
}

function routeAfterLogin() {
  const r = parseRoute();
  return (r && r !== 'login' && r !== 'register' && ROUTES.includes(r)) ? r : 'main';
}

const App = {
  init() {
    try {
      if (typeof firebase === 'undefined') throw new Error('Firebase SDK');
      Db.init();
    } catch (e) {
      console.error(e);
      showToast('Firebase не загружен. Проверьте интернет.');
      return;
    }

    window.__currentUser = null;
    this.bindLogin();
    document.getElementById('linkRegister')?.addEventListener('click', e => {
      e.preventDefault();
      this.go('register');
    });

    Db.auth.onAuthStateChanged(fu => this.onAuthUser(fu));

    document.getElementById('app').addEventListener('click', e => {
      const el = e.target.closest('[data-route]');
      if (!el) return;
      e.preventDefault();
      if (el.classList.contains('disabled')) {
        showToast('Нет доступа');
        return;
      }
      if (el.dataset.requiresGroup && currentUser && !groupName(currentUser)) {
        showToast('У вас не указана группа. Обратитесь к администратору.');
        return;
      }
      const r = el.dataset.route;
      if (r === 'back') this.go('back');
      else if (r.includes('?')) { location.hash = r; this.go(r.split('?')[0]); }
      else this.go(r);
    });

    window.addEventListener('hashchange', () => {
      if (!Db.auth.currentUser || !currentUser) return;
      const r = parseRoute();
      if (r === 'login' || r === 'register') return;
      if (r !== backStack[backStack.length - 1]) this.go(r, true);
    });
  },

  showLogin() {
    document.getElementById('app').innerHTML = UI.login();
    this.bindLogin();
    location.hash = 'login';
  },

  async onAuthUser(fu) {
    if (fu) {
      const btn = document.getElementById('loginBtn');
      if (btn) { btn.disabled = true; btn.textContent = 'Вход...'; }
      try {
        currentUser = await withTimeout(Db.fetchUser(fu.uid), 10000, null);
        if (!currentUser) {
          showToast('Профиль не найден. Проверьте доступ к Firestore.');
          await Db.auth.signOut().catch(() => {});
          if (btn) { btn.disabled = false; btn.textContent = 'Войти'; }
          return;
        }
        window.__currentUser = currentUser;
        showToast('Вход успешен!');
        const route = routeAfterLogin();
        this.go(route, true);
      } catch (e) {
        console.error(e);
        showToast('Ошибка загрузки профиля');
        if (btn) { btn.disabled = false; btn.textContent = 'Войти'; }
      }
    } else {
      currentUser = null;
      window.__currentUser = null;
      if (parseRoute() === 'register') this.go('register', true);
      else this.showLogin();
    }
  },

  go(route, replace = false) {
    let routeName = route;
    let queryPart = '';
    if (typeof route === 'string' && route.includes('?')) {
      const parts = route.split('?');
      routeName = parts[0];
      queryPart = '?' + parts.slice(1).join('?');
    } else if (typeof routeName === 'string') {
      const cur = location.hash.slice(1);
      const curParts = cur.split('?');
      if (curParts[0] === routeName && curParts.length > 1) queryPart = '?' + curParts.slice(1).join('?');
    }
    if (chatUnsub) { chatUnsub(); chatUnsub = null; }
    if (roomUnsub) { roomUnsub(); roomUnsub = null; }
    if (scheduleUnsub) { scheduleUnsub(); scheduleUnsub = null; }
    if (notifUnsub) { notifUnsub(); notifUnsub = null; }
    if (!replace && routeName !== 'back') {
      if (routeName === 'main') backStack = ['main'];
      else if (backStack[backStack.length - 1] !== routeName) backStack.push(routeName);
    }
    if (routeName === 'back') {
      backStack.pop();
      routeName = backStack[backStack.length - 1] || 'main';
      queryPart = '';
    }
    const hash = routeName + queryPart;
    if (location.hash.slice(1) !== hash) location.hash = hash;
    if (currentUser && !canAccessRoute(routeName, currentUser)) {
      showToast('Нет доступа');
      if (routeName !== 'main') {
        routeName = 'main';
        queryPart = '';
        location.hash = 'main';
      } else return;
    }
    const app = document.getElementById('app');
    const pages = {
      login: () => { app.innerHTML = UI.login(); this.bindLogin(); },
      register: () => { app.innerHTML = UI.register(); this.bindRegister(); },
      privacy: () => { app.innerHTML = UI.privacy(); this.bindNav(); },
      main: () => this.pageMain(),
      grades: () => this.pageGrades(),
      absences: () => this.pageAbsences(false),
      'manage-absences': () => this.pageAbsences(true),
      schedule: () => this.pageSchedule(),
      events: () => this.pageEvents(),
      nutrition: () => this.pageNutrition(),
      clubs: () => this.pageClubs(),
      club: () => this.pageClubDetail(),
      notifications: () => this.pageNotifications(),
      chat: () => this.pageChat(),
      profile: () => this.pageProfile(),
      'edit-profile': () => this.pageEditProfile(),
      teacher: () => this.pageTeacherHub(),
      headman: () => this.pageHeadmanHub(),
      admin: () => this.pageAdminHub(),
      journal: () => this.pageJournal(),
      students: () => this.pageStudents(),
      'student-info': () => this.pageStudentInfo(),
      'student-grades': () => this.pageStudentGrades(),
      statistics: () => this.pageStatistics(),
      curatorial: () => this.pageCuratorial(),
      'admin-catalog': () => this.pageAdminCatalog(),
      'admin-club-leaders': () => this.pageAdminClubLeaders()
    };
    (pages[routeName] || pages.main)();
  },

  bindNav() {
    /* Навигация через делегирование в init() */
  },

  bindLogin() {
    const LOGIN_TIMEOUT = { __timeout: true };
    setupPasswordToggles();
    clearAuthFieldErrors();
    const run = async () => {
      clearAuthFieldErrors();
      const email = document.getElementById('email').value.trim();
      const password = document.getElementById('password').value;
      if (!email || !password) {
        setAuthFieldError('email', 'emailError', 'Заполните все поля');
        document.getElementById('email')?.focus();
        return;
      }
      const btn = document.getElementById('loginBtn');
      const sp = document.getElementById('loginSpinner');
      btn.disabled = true;
      btn.textContent = 'Вход...';
      sp.classList.add('visible');
      try {
        const result = await withTimeout(
          Db.auth.signInWithEmailAndPassword(email, password),
          20000,
          LOGIN_TIMEOUT
        );
        if (result === LOGIN_TIMEOUT) throw { code: 'auth/network-request-failed' };
      } catch (e) {
        const m = {
          'auth/wrong-password': 'Неверный пароль',
          'auth/invalid-credential': 'Неверный email или пароль',
          'auth/user-not-found': 'Пользователь не найден',
          'auth/network-request-failed': 'Проверьте подключение к интернету',
          'auth/too-many-requests': 'Слишком много попыток. Подождите.'
        };
        showToast('Ошибка: ' + (m[e.code] || e.message || 'Ошибка входа'));
        btn.disabled = false;
        btn.textContent = 'Войти';
        sp.classList.remove('visible');
      }
    };
    document.getElementById('loginBtn').onclick = run;
    document.getElementById('password').onkeydown = e => { if (e.key === 'Enter') run(); };
    document.getElementById('email').onkeydown = e => { if (e.key === 'Enter') run(); };
    this.bindNav();
  },

  bindRegister() {
    setupPasswordToggles();
    clearAuthFieldErrors();
    const genderEl = document.getElementById('gender');
    const roleEl = document.getElementById('role');
    refreshRegisterRoleOptions(genderEl, roleEl, false);
    genderEl.onchange = () => {
      setAuthFieldError('gender', 'genderError', '');
      refreshRegisterRoleOptions(genderEl, roleEl, true);
    };
    document.getElementById('registerBtn').onclick = async () => {
      clearAuthFieldErrors();
      const data = {
        fullName: document.getElementById('fullName').value.trim(),
        email: document.getElementById('email').value.trim(),
        password: document.getElementById('password').value,
        gender: document.getElementById('gender').value,
        group: document.getElementById('groupName').value.trim(),
        role: document.getElementById('role').value
      };
      let hasError = false;
      if (!data.email) {
        setAuthFieldError('email', 'emailError', 'Введите email');
        document.getElementById('email').focus();
        hasError = true;
      }
      if (!data.password) {
        setAuthFieldError('password', 'passwordError', 'Введите пароль');
        if (!hasError) document.getElementById('password').focus();
        hasError = true;
      } else if (data.password.length < 6) {
        setAuthFieldError('password', 'passwordError', 'Пароль должен быть не менее 6 символов');
        if (!hasError) document.getElementById('password').focus();
        hasError = true;
      }
      if (!data.fullName) {
        setAuthFieldError('fullName', 'fullNameError', 'Введите ФИО');
        if (!hasError) document.getElementById('fullName').focus();
        hasError = true;
      }
      if (!data.gender) {
        setAuthFieldError('gender', 'genderError', 'Выберите пол');
        hasError = true;
      }
      if (!data.group) {
        setAuthFieldError('groupName', 'groupError', 'Введите группу');
        if (!hasError) document.getElementById('groupName').focus();
        hasError = true;
      }
      if (hasError) return;

      const groupId = groupNameToDocumentId(data.group);
      const btn = document.getElementById('registerBtn');
      const sp = document.getElementById('registerSpinner');
      btn.disabled = true;
      sp.classList.add('visible');
      try {
        const allowed = await Db.canRegisterToGroup(data.role, groupId);
        if (!allowed) {
          showToast('Лимит для группы превышен. Регистрация невозможна.');
          return;
        }
        const cred = await Db.auth.createUserWithEmailAndPassword(data.email, data.password);
        await Db.registerUser(cred, data);
        showToast('Регистрация успешна!');
      } catch (e) {
        if (Db.auth.currentUser && !currentUser) {
          await Db.auth.currentUser.delete().catch(() => {});
          await Db.auth.signOut().catch(() => {});
        }
        showToast('Ошибка: ' + (e.message?.includes('Лимит') ? e.message : mapRegisterFirebaseError(e)));
      } finally {
        btn.disabled = false;
        sp.classList.remove('visible');
      }
    };
    this.bindNav();
  },

  async pageMain() {
    if (!currentUser) return this.showLogin();
    const stats = { avg: '—', gradesCount: 0, absenceHours: 0 };
    document.getElementById('app').innerHTML = UI.main(currentUser, stats, 0);
    this.bindNav();
    this.refreshMainStats();
    if (isStudent(currentUser) || isHeadman(currentUser)) {
      Db.ensureWeeklyMealAutoPlan(currentUser).then(u => {
        if (u && u.id === currentUser?.id) {
          currentUser = u;
          window.__currentUser = u;
        }
      }).catch(console.error);
    }
  },

  refreshMainStats() {
    if (!currentUser) return;
    const stats = { avg: '—', gradesCount: 0, absenceHours: 0 };
    const vis = mainMenuVisibility(currentUser);
    const loadStats = async () => {
      if (!vis.quickStats) return stats;
      try {
        const sem = await withTimeout(resolveCurrentSemester(currentUser), 8000, null);
        let grades = (await withTimeout(Db.getStudentGrades(currentUser.id), 8000, [])).filter(isVisibleGrade);
        let absences = await withTimeout(Db.getStudentAbsences(currentUser.id), 8000, []);
        if (sem) {
          grades = filterBySemester(grades, sem);
          absences = filterBySemester(absences, sem);
        }
        const forAvg = grades.filter(g => g.value >= 1 && g.value <= 10);
        const totalGrades = grades.length;
        const avg = forAvg.length ? forAvg.reduce((s, g) => s + g.value, 0) / forAvg.length : 0;
        return {
          avg: totalGrades > 0 && forAvg.length ? avg.toFixed(1) : '—',
          gradesCount: totalGrades,
          absenceHours: absences.reduce((s, a) => s + (a.hours || 0), 0)
        };
      } catch (e) {
        console.error(e);
        return stats;
      }
    };
    Promise.all([
      loadStats(),
      withTimeout(Db.getUnreadCount(currentUser.id).catch(() => 0), 8000, 0)
    ]).then(([loadedStats, unread]) => {
      if (parseRoute() !== 'main' || !currentUser) return;
      document.getElementById('app').innerHTML = UI.main(currentUser, loadedStats, unread);
      this.bindNav();
    }).catch(console.error);
  },

  async pageGrades() {
    document.getElementById('app').innerHTML = `<div class="page-scroll main-screen">
      ${UI.screenHeader('Мои отметки', '📊', 'green menu-icon', 'grades', { exportId: 'exportGradesBtn' })}
      <div id="gradesStats">${UI.gradesStatsCard('—', 0)}</div>
      <div id="gradesFilters">${UI.gradesFilters('<option value="">Все предметы</option>', '<option value="">Все семестры</option>')}</div>
      <div class="page-content" id="gradesBody">${loadingHtml()}</div>
    </div>`;
    this.bindNav();
    let all = (await Db.getStudentGrades(currentUser.id)).filter(isVisibleGrade);
    const subjects = [...new Set(all.map(g => g.subject))].filter(Boolean);
    const gid = effectiveGroupId(currentUser);
    const semesters = await Db.getSemesters(gid).catch(() => []);
    const currentSem = await resolveCurrentSemester(currentUser);
    const currentSemNum = currentSem ? semesterNumber(currentSem.name) : null;
    const subSel = document.getElementById('fSubject');
    const semSel = document.getElementById('fSemester');
    subjects.forEach(s => { const o = document.createElement('option'); o.value = s; o.textContent = s; subSel.appendChild(o); });
    for (let i = 1; i <= 8; i++) {
      const o = document.createElement('option');
      o.value = i;
      o.textContent = `${i} семестр`;
      if (currentSemNum === i) o.selected = true;
      semSel.appendChild(o);
    }
    let filtered = all;
    const render = () => {
      filtered = all;
      if (subSel.value) filtered = filtered.filter(g => g.subject === subSel.value);
      if (semSel.value) filtered = filtered.filter(g => String(g.semester) === semSel.value);
      const fa = filtered.filter(g => g.value >= 1 && g.value <= 10);
      const avg = fa.length ? (fa.reduce((s, g) => s + g.value, 0) / fa.length).toFixed(1) : '—';
      document.getElementById('gradesStats').innerHTML = UI.gradesStatsCard(avg, filtered.length);
      document.getElementById('gradesBody').innerHTML = UI.gradesList(filtered);
    };
    subSel.onchange = semSel.onchange = render;
    document.getElementById('exportGradesBtn').onclick = () => {
      exportPrint('Мои отметки', ['Предмет', 'Отметка', 'Дата', 'Тип', 'Семестр', 'Преподаватель'],
        filtered.map(g => [g.subject, g.value, g.date, g.type, g.semester || '', g.teacherName]));
    };
    render();
  },

  async pageAbsences(manage) {
    const gn = groupName(currentUser);
    const title = manage ? 'Система посещаемости' : 'Мои пропуски';
    const subtitle = manage ? 'Управление пропусками' : '';
    document.getElementById('app').innerHTML = `<div class="page-scroll main-screen">
      ${UI.screenHeader(title, '📋', 'orange menu-icon', 'absences', { subtitle, exportId: 'exportAbsBtn' })}
      ${manage ? '<div class="page-content"><button class="btn-primary" id="addAbsenceBtn" style="margin-bottom:12px">+ Добавить пропуск</button></div>' : ''}
      <div class="page-content" id="absBody">${loadingHtml()}</div>
    </div>`;
    this.bindNav();
    const list = manage ? await Db.getGroupAbsences(gn) : await Db.getStudentAbsences(currentUser.id);
    const totalH = list.reduce((s, a) => s + (a.hours || 0), 0);
    const excH = list.filter(a => a.isExcused ?? a.excused).reduce((s, a) => s + (a.hours || 0), 0);
    document.getElementById('absBody').innerHTML =
      `<div class="stats-hero-card abs-stats">
        <div class="stats-hero-col"><div class="stats-hero-value stat-amber">${totalH}</div><div class="stats-hero-label">Всего ч.</div></div>
        <div class="stats-hero-divider"></div>
        <div class="stats-hero-col"><div class="stats-hero-value stat-emerald">${excH}</div><div class="stats-hero-label">Уваж.</div></div>
        <div class="stats-hero-divider"></div>
        <div class="stats-hero-col"><div class="stats-hero-value stat-purple">${totalH - excH}</div><div class="stats-hero-label">Неуваж.</div></div>
      </div>` + UI.absencesList(list, manage);
    if (manage) document.getElementById('addAbsenceBtn').onclick = () => this.dialogAbsence(null);
    document.querySelectorAll('[data-absence-id]').forEach(el => {
      if (manage) el.onclick = () => {
        const a = list.find(x => x.id === el.dataset.absenceId);
        if (a && canModify(currentUser, a.createdBy, a.createdByRole)) this.dialogAbsence(a);
      };
    });
    const ex = document.getElementById('exportAbsBtn');
    if (ex) ex.onclick = () => {
      const headers = manage
        ? ['Учащийся', 'Предмет', 'Дата', 'Часы', 'Причина', 'Уваж.']
        : ['Предмет', 'Дата', 'Часы', 'Причина', 'Уваж.'];
      const rows = manage
        ? list.map(a => [a.studentName, a.subject, a.date, a.hours, absenceReasonLabel(a.reason), (a.isExcused ?? a.excused) ? 'Да' : 'Нет'])
        : list.map(a => [a.subject, a.date, a.hours, absenceReasonLabel(a.reason), (a.isExcused ?? a.excused) ? 'Да' : 'Нет']);
      exportPrint(manage ? `Пропуски — ${gn}` : 'Мои пропуски', headers, rows);
    };
  },

  async dialogAbsence(existing) {
    const students = await Db.getGroupStudents(groupName(currentUser));
    const gid = effectiveGroupId(currentUser);
    const subjects = (await Db.getSubjects(gid)).map(s => s.name);
    const stOpts = students.map(s => `<option value="${s.id}" ${existing?.studentId === s.id ? 'selected' : ''}>${escapeHtml(s.fullName)}</option>`).join('');
    const subOpts = subjects.map(s => `<option ${existing?.subject === s ? 'selected' : ''}>${escapeHtml(s)}</option>`).join('');
    const reasonOpts = ABSENCE_REASONS.map(r => `<option value="${r.v}" ${existing?.reason === r.v ? 'selected' : ''}>${escapeHtml(r.l)}</option>`).join('');
    const hourOpts = [2, 4, 6, 8].map(h => `<option ${existing?.hours === h ? 'selected' : ''}>${h}</option>`).join('');
    const btns = [{ id: 'save', label: 'Сохранить' }, { id: 'cancel', label: 'Отмена', class: 'btn-outline' }];
    if (existing) btns.splice(1, 0, { id: 'del', label: 'Удалить', class: 'btn-outline' });
    const act = await showModal(existing ? 'Изменить пропуск' : 'Добавить пропуск', `
      <label class="form-label">Учащийся</label><select class="form-select" id="dStudent">${stOpts}</select>
      <label class="form-label">Предмет</label><select class="form-select" id="dSubject">${subOpts || '<option>—</option>'}</select>
      <label class="form-label">Дата (ДД.ММ.ГГГГ)</label><input class="form-input" id="dDate" value="${escapeHtml(existing?.date || toDateString(new Date()))}">
      <label class="form-label">Часы</label><select class="form-select" id="dHours">${hourOpts}</select>
      <label class="form-label">Причина</label><select class="form-select" id="dReason">${reasonOpts}</select>
      <label class="form-label"><input type="checkbox" id="dExcused" ${(existing?.isExcused ?? existing?.excused) ? 'checked' : ''}> Уважительная</label>
      <label class="form-label">Комментарий</label><input class="form-input" id="dComment" value="${escapeHtml(existing?.comment || '')}">`,
      btns);
    if (act === 'cancel') return;
    if (act === 'del' && existing) { await Db.deleteAbsence(existing.id); showToast('Удалено'); return this.pageAbsences(true); }
    const stId = document.getElementById('dStudent').value;
    const st = students.find(s => s.id === stId);
    const absence = {
      id: existing?.id, studentId: stId, studentName: st?.fullName || '',
      subject: document.getElementById('dSubject').value.trim(),
      date: document.getElementById('dDate').value.trim(),
      hours: parseInt(document.getElementById('dHours').value, 10) || 2,
      reason: document.getElementById('dReason').value,
      excused: document.getElementById('dExcused').checked,
      comment: document.getElementById('dComment').value.trim(),
      studentGroup: groupName(currentUser), createdBy: currentUser.id,
      createdByName: currentUser.fullName, createdByRole: currentUser.role
    };
    await Db.saveAbsence(absence, !existing);
    showToast('Сохранено');
    this.pageAbsences(true);
  },

  pageSchedule() {
    const gn = groupName(currentUser);
    const canEdit = canEditEvents(currentUser);
    if (!window._schedDate) {
      let d = new Date();
      while (d.getDay() === 0 || d.getDay() === 6) d = nextWeekday(d, 1);
      window._schedDate = toDateString(d);
    }
    const dateStr = window._schedDate;
    document.getElementById('app').innerHTML = `<div class="page-scroll main-screen">
      ${UI.screenHeader('Расписание', '📚', 'blue menu-icon', 'blue', { subtitle: gn || 'Ваше расписание на неделю' })}
      <div class="page-content">
        <div class="week-nav"><button class="btn-sm" id="schedPrev">‹</button>
        <span class="week-label">${escapeHtml(dateStr)}<br><small>${dayNameFromDate(dateStr)}</small></span>
        <button class="btn-sm" id="schedNext">›</button></div>
        ${canEdit ? '<button class="btn-primary" id="addSchedBtn" style="margin-bottom:12px">+ Занятие</button>' : ''}
        <div id="schedBody">${loadingHtml()}</div></div></div>`;
    this.bindNav();
    const render = (items) => {
      const dayItems = filterScheduleByDate(items, dateStr);
      document.getElementById('schedBody').innerHTML = dayItems.length
        ? UI.scheduleList(dayItems, { singleDay: true }) : `<div class="empty-state"><span>📚</span>Нет занятий на ${escapeHtml(dateStr)}</div>`;
      if (canEdit) {
        document.getElementById('addSchedBtn').onclick = () => this.dialogSchedule(null, gn, dateStr, 'schedule');
        document.querySelectorAll('[data-schedule-id]').forEach(el => {
          el.onclick = () => {
            const item = dayItems.find(x => x.id === el.dataset.scheduleId);
            if (item && canModify(currentUser, item.createdBy, item.createdByRole)) this.dialogSchedule(item, gn, dateStr, 'schedule');
          };
        });
      }
    };
    document.getElementById('schedPrev').onclick = () => {
      window._schedDate = toDateString(nextWeekday(parseDate(dateStr), -1));
      this.pageSchedule();
    };
    document.getElementById('schedNext').onclick = () => {
      window._schedDate = toDateString(nextWeekday(parseDate(dateStr), 1));
      this.pageSchedule();
    };
    if (scheduleUnsub) scheduleUnsub();
    scheduleUnsub = Db.subscribeSchedule(gn, render);
  },

  async dialogSchedule(item, gn, dateStr, backRoute = 'schedule') {
    const gid = effectiveGroupId(currentUser);
    const ds = dateStr || item?.date || toDateString(new Date());
    const [subjects, catalogTeachers, allSchedule, semesters] = await Promise.all([
      Db.getSubjects(gid, gn), Db.getCatalogTeachers(), Db.getScheduleForGroup(gn), Db.getSemesters(gid, gn)
    ]);
    const subjectNames = subjects.map(s => s.name).filter(Boolean);
    const allTeacherNames = catalogTeachers.map(t => t.fullName).filter(Boolean);
    const teachersBySubject = {};
    subjects.forEach(sub => {
      const sid = subjectDocumentId(sub.name, sub.groupId || gid);
      const names = catalogTeachers.filter(t => (t.subjectIds || []).includes(sid)).map(t => t.fullName);
      if (names.length) teachersBySubject[sub.name] = names;
    });
    const dayItems = allSchedule.filter(s => s.date === ds && s.id !== item?.id);
    const usedSlots = new Set(dayItems.map(s => s.time));
    const hasLunchOnDate = allSchedule.some(s => s.type === 'LUNCH' && s.date === ds && s.id !== item?.id);
    const isInfo = (item?.type || '') === 'INFO_HOUR';
    const isCuratorialRoute = backRoute === 'curatorial';
    let students = [];
    if (isInfo || item?.type === 'INFO_HOUR') {
      students = await Db.getGroupStudents(gn).catch(() => []);
    }
    const assigned = new Set(item?.assignedStudentIds || []);
    const curType = item?.type || (isCuratorialRoute ? 'CURATOR_HOUR' : 'LECTURE');
    const isPairTime = PAIR_NUMBERS.includes(item?.time);
    const typeKeys = Object.keys(SCHEDULE_TYPES).filter(k => k !== 'LUNCH' || (!hasLunchOnDate && curType !== 'LUNCH'));
    if (isCuratorialRoute) {
      typeKeys.length = 0;
      typeKeys.push('CURATOR_HOUR', 'INFO_HOUR');
    }
    const typeOpts = typeKeys.map(k =>
      `<option value="${k}" ${curType === k ? 'selected' : ''}>${escapeHtml(SCHEDULE_TYPES[k])}</option>`).join('');
    const subjectList = subjectNames.map(s => `<option value="${escapeHtml(s)}">`).join('');
    const studentChecks = students.length && (isInfo || item?.type === 'INFO_HOUR' || (!item && isCuratorialRoute))
      ? `<div class="form-label">Учащиеся (информационный час)</div>
        <div class="student-checks" style="max-height:160px;overflow:auto">${students.map(s =>
          `<label style="display:block;margin:4px 0"><input type="checkbox" class="info-student" value="${s.id}" ${assigned.has(s.id) ? 'checked' : ''}> ${escapeHtml(s.fullName)}</label>`
        ).join('')}</div>` : '';
    const btns = [{ id: 'save', label: 'Сохранить' }, { id: 'cancel', label: 'Отмена', class: 'btn-outline' }];
    if (item) btns.splice(1, 0, { id: 'del', label: 'Удалить', class: 'btn-outline' });
    const isHourType = curType === 'CURATOR_HOUR' || curType === 'INFO_HOUR' || isCuratorialRoute;
    const timeFieldHtml = isHourType
      ? `<label class="form-label">Время</label><input class="form-input" id="dTimeSimple" value="${escapeHtml(item?.time || '14:00')}">
         <label class="form-label">Аудитория</label><input class="form-input" id="dRoom" value="${escapeHtml(item?.room || '')}">`
      : `<div class="radio-row" id="pairLessonRow">
          <label><input type="radio" name="timeMode" value="pair" ${isPairTime ? 'checked' : ''}> Пара</label>
          <label><input type="radio" name="timeMode" value="lesson" ${!isPairTime ? 'checked' : ''}> Урок</label>
        </div>
        <label class="form-label">Время</label>
        <select class="form-select" id="dTimePair">${selectOptions(PAIR_NUMBERS.filter(t => !usedSlots.has(t) || t === item?.time), item?.time && isPairTime ? item.time : null, null)}</select>
        <select class="form-select hidden" id="dTimeLesson">${selectOptions(LESSON_NUMBERS.filter(t => !usedSlots.has(t) || t === item?.time), item?.time && !isPairTime ? item.time : null, null)}</select>`;
    const act = await showModal(item ? 'Изменить занятие' : 'Добавить занятие', `
      <label class="form-label">Дата (ДД.ММ.ГГГГ)</label><input class="form-input" id="dDate" value="${escapeHtml(item?.date || ds)}">
      <label class="form-label">Тип</label><select class="form-select" id="dType">${typeOpts}</select>
      <div id="schedDetailFields">
        <label class="form-label">${isHourType ? 'Тема' : 'Предмет'}</label>
        <input class="form-input" id="dSubject" ${isHourType ? '' : 'list="subjectList"'} value="${escapeHtml(item?.subject || '')}">
        ${isHourType ? '' : `<datalist id="subjectList">${subjectList}</datalist>`}
        ${timeFieldHtml}
        ${isHourType ? '' : `<div class="radio-row">
          <label><input type="radio" name="groupMode" value="whole" ${!item?.isSubgroup ? 'checked' : ''}> Вся группа</label>
          <label><input type="radio" name="groupMode" value="sub" ${item?.isSubgroup ? 'checked' : ''}> Подгруппы</label>
        </div>
        <label class="form-label">Преподаватель 1</label>
        <input class="form-input" id="dTeacher" list="teacherList1" value="${escapeHtml(item?.teacherName || currentUser.fullName)}">
        <datalist id="teacherList1"></datalist>
        <label class="form-label">Аудитория 1</label><input class="form-input" id="dRoom" value="${escapeHtml(item?.room || '')}">
        <div id="subgroupFields" class="subgroup-fields ${item?.isSubgroup ? '' : 'hidden'}">
          <label class="form-label">Преподаватель 2</label>
          <input class="form-input" id="dTeacher2" list="teacherList2" value="${escapeHtml(item?.teacherName2 || '')}">
          <datalist id="teacherList2"></datalist>
          <label class="form-label">Аудитория 2</label><input class="form-input" id="dRoom2" value="${escapeHtml(item?.room2 || '')}">
        </div>`}
      </div>
      <label class="form-label">Описание</label><input class="form-input" id="dDesc" value="${escapeHtml(item?.description || '')}">
      ${studentChecks}`,
      btns,
      overlay => {
        if (isHourType) return;
        const fillTeachers = (subject) => {
          const names = teachersBySubject[subject] || allTeacherNames;
          ['teacherList1', 'teacherList2'].forEach(id => {
            const dl = overlay.querySelector('#' + id);
            if (dl) dl.innerHTML = names.map(n => `<option value="${escapeHtml(n)}">`).join('');
          });
        };
        fillTeachers(item?.subject || '');
        const syncTimeMode = () => {
          const pair = overlay.querySelector('input[name="timeMode"]:checked')?.value === 'pair';
          overlay.querySelector('#dTimePair')?.classList.toggle('hidden', !pair);
          overlay.querySelector('#dTimeLesson')?.classList.toggle('hidden', pair);
        };
        const syncLunch = () => {
          const lunch = overlay.querySelector('#dType')?.value === 'LUNCH';
          overlay.querySelector('#schedDetailFields')?.classList.toggle('hidden', lunch);
          overlay.querySelector('#pairLessonRow')?.classList.toggle('hidden', lunch);
        };
        overlay.querySelector('#dSubject')?.addEventListener('change', e => fillTeachers(e.target.value));
        overlay.querySelector('#dSubject')?.addEventListener('input', e => fillTeachers(e.target.value));
        overlay.querySelectorAll('input[name="timeMode"]').forEach(r => r.addEventListener('change', syncTimeMode));
        overlay.querySelectorAll('input[name="groupMode"]').forEach(r => {
          r.addEventListener('change', () => {
            overlay.querySelector('#subgroupFields')?.classList.toggle('hidden', r.value !== 'sub');
          });
        });
        overlay.querySelector('#dType')?.addEventListener('change', syncLunch);
        syncTimeMode();
        syncLunch();
      });
    if (act === 'cancel') return;
    if (act === 'del' && item) { await Db.deleteSchedule(item.id); showToast('Удалено'); return this.go(backRoute); }
    if (act !== 'save') return;

    const schedType = document.getElementById('dType').value;
    const dateVal = document.getElementById('dDate').value.trim();
    if (isCuratorialRoute && !isDateInAnySemester(dateVal, semesters)) {
      showToast('Дата должна попадать в семестр учебного года');
      return;
    }
    const isSubgroup = !isHourType && document.querySelector('input[name="groupMode"]:checked')?.value === 'sub';
    const isLunch = schedType === 'LUNCH';
    const isPair = !isHourType && document.querySelector('input[name="timeMode"]:checked')?.value === 'pair';
    const time = isHourType
      ? (document.getElementById('dTimeSimple')?.value || '').trim()
      : (isLunch || isPair
        ? (document.getElementById('dTimePair')?.value || '').trim()
        : (document.getElementById('dTimeLesson')?.value || '').trim());
    if (!time) { showToast('Укажите время'); return; }
    if (isSubgroup && !isLunch) {
      if (!document.getElementById('dTeacher2')?.value.trim()) { showToast('Выберите преподавателя для подгруппы 2'); return; }
      if (!document.getElementById('dRoom2')?.value.trim()) { showToast('Введите аудиторию для подгруппы 2'); return; }
    }
    const schedTypeFinal = schedType;
    const assignedStudentIds = schedTypeFinal === 'INFO_HOUR'
      ? [...document.querySelectorAll('.info-student:checked')].map(c => c.value) : (item?.assignedStudentIds || []);
    const data = {
      id: item?.id, group: gn,
      date: dateVal,
      day: dayNameFromDate(dateVal),
      time: time || item?.time || '14:00',
      subject: isLunch ? 'Обед' : document.getElementById('dSubject').value.trim(),
      room: isLunch ? '' : (document.getElementById('dRoom')?.value.trim() || ''),
      room2: isSubgroup && !isLunch ? (document.getElementById('dRoom2')?.value.trim() || '') : '',
      teacherName: isLunch ? '' : (document.getElementById('dTeacher')?.value.trim() || currentUser.fullName),
      teacherName2: isSubgroup && !isLunch ? (document.getElementById('dTeacher2')?.value.trim() || '') : '',
      isSubgroup: isSubgroup && !isLunch,
      description: document.getElementById('dDesc').value.trim(),
      type: schedTypeFinal,
      assignedStudentIds,
      createdBy: item?.createdBy || currentUser.id,
      createdByRole: item?.createdByRole || currentUser.role
    };
    await Db.saveSchedule(data, !item?.id);
    showToast('Сохранено');
    this.go(backRoute);
  },

  async pageEvents() {
    const gn = groupName(currentUser);
    const canEdit = canEditEvents(currentUser);
    if (!window._eventDate) window._eventDate = toDateString(new Date());
    const dateStr = window._eventDate;
    document.getElementById('app').innerHTML = `<div class="page-scroll main-screen">
      ${UI.screenHeader('Мероприятия', '🎉', 'purple menu-icon', 'purple', { subtitle: 'События группы' })}
      <div class="page-content">
        <div class="week-nav"><button class="btn-sm" id="evPrev">‹</button>
        <span class="week-label">${escapeHtml(dateStr)}</span><button class="btn-sm" id="evNext">›</button></div>
        ${canEdit ? '<button class="btn-primary" id="addEventBtn" style="margin-bottom:12px">+ Мероприятие</button>' : ''}
        <div id="evBody">${loadingHtml()}</div></div></div>`;
    this.bindNav();
    const events = (await Db.getGroupEvents(gn)).filter(e => e.date === dateStr);
    document.getElementById('evBody').innerHTML = UI.eventsList(events, canEdit);
    document.getElementById('evPrev').onclick = () => { window._eventDate = toDateString(nextWeekday(parseDate(dateStr), -1)); this.pageEvents(); };
    document.getElementById('evNext').onclick = () => { window._eventDate = toDateString(nextWeekday(parseDate(dateStr), 1)); this.pageEvents(); };
    if (canEdit) document.getElementById('addEventBtn').onclick = () => this.dialogEvent(null, gn, dateStr);
    document.querySelectorAll('[data-edit-event]').forEach(btn => btn.onclick = () => {
      const ev = events.find(e => e.id === btn.dataset.editEvent);
      if (ev) this.dialogEvent(ev, gn, dateStr);
    });
    document.querySelectorAll('[data-del-event]').forEach(btn => btn.onclick = async () => {
      await Db.deleteEvent(btn.dataset.delEvent);
      showToast('Удалено');
      this.pageEvents();
    });
  },

  async dialogEvent(ev, gn, dateStr) {
    const act = await showModal(ev ? 'Изменить' : 'Новое мероприятие', `
      <label class="form-label">Название</label><input class="form-input" id="dTitle" value="${escapeHtml(ev?.title || '')}">
      <label class="form-label">Дата</label><input class="form-input" id="dDate" value="${escapeHtml(ev?.date || dateStr || toDateString(new Date()))}">
      <label class="form-label">Время</label><input class="form-input" id="dTime" value="${escapeHtml(ev?.time || '12:00')}">
      <label class="form-label">Место</label><input class="form-input" id="dPlace" value="${escapeHtml(ev?.place || '')}">
      <label class="form-label">Описание</label><input class="form-input" id="dDesc" value="${escapeHtml(ev?.description || '')}">`,
      [{ id: 'save', label: 'Сохранить' }, { id: 'cancel', label: 'Отмена', class: 'btn-outline' }]);
    if (act !== 'save') return;
    const data = {
      id: ev?.id, title: document.getElementById('dTitle').value.trim(),
      date: document.getElementById('dDate').value.trim(), time: document.getElementById('dTime').value.trim(),
      place: document.getElementById('dPlace').value.trim(), description: document.getElementById('dDesc').value.trim(),
      groupName: gn, createdBy: currentUser.id, createdByName: currentUser.fullName, createdByRole: currentUser.role
    };
    await Db.saveEvent(data, !ev);
    showToast('Сохранено');
    this.pageEvents();
  },

  async pageNutrition() {
    let date = nutritionDate ? parseDate(nutritionDate) : new Date();
    if (!date) date = new Date();
    while (date.getDay() === 0 || date.getDay() === 6) date = nextWeekday(date, 1);
    const dateStr = toDateString(date);
    nutritionDate = dateStr;
    const weekend = isWeekend(dateStr);
    const sub = await Db.getMealSubscription(dateStr, currentUser.id);
    const gn = groupName(currentUser);
    const subs = await Db.getMealSubscribers(dateStr, gn);
    const canRoster = isTeacher(currentUser) || isHeadman(currentUser);
    const isLearner = isStudent(currentUser) || isHeadman(currentUser);
    const statusText = weekend && !sub?.isSubscribed
      ? 'В выходные (СБ/ВС) запись на питание недоступна'
      : sub?.isSubscribed ? `Вы записаны на питание на ${dateStr}` : `Вы не записаны на питание на ${dateStr}`;
    document.getElementById('app').innerHTML = `<div class="page-scroll main-screen">
      ${UI.screenHeader('Питание', '🍽️', 'green menu-icon', 'green', { subtitle: dateStr })}
      <div class="page-content">
        <div class="week-nav" style="margin-bottom:16px">
          <button class="btn-sm" id="mealPrev">‹</button>
          <span class="week-label">${escapeHtml(dateStr)}<br><small>${dayNameFromDate(dateStr)}</small></span>
          <button class="btn-sm" id="mealNext">›</button>
        </div>
        ${isLearner ? `<div class="list-card">
          <div class="list-card-title">Статус питания</div>
          <div class="list-card-body">${escapeHtml(statusText)}</div>
          <button class="btn-primary" style="margin-top:12px" id="toggleMeal" ${weekend && !sub?.isSubscribed ? 'disabled' : ''}>
            ${sub?.isSubscribed ? 'Отписаться от питания' : 'Записаться на питание'}</button>
          <button class="btn-sm" style="margin-top:8px" id="weeklyPlanBtn" ${weekend ? 'disabled' : ''}>Автоплан Пн–Пт</button>
          <label class="form-label" style="margin-top:12px;display:flex;align-items:center;gap:8px">
            <input type="checkbox" id="weeklyAutoSwitch" ${currentUser.mealAutoPlanEnabled ? 'checked' : ''}>
            Повторять автоплан каждую неделю</label>
        </div>` : ''}
        ${canRoster ? `<div class="menu-title">Список записавшихся (${subs.length})</div>
          <button class="btn-sm" id="exportMeal" style="margin-bottom:12px">💾 Экспорт</button>
          ${subs.length ? subs.map((s, i) => `<div class="list-card"><span class="list-card-meta">${i + 1}.</span> ${escapeHtml(s.userName)}</div>`).join('')
            : '<p class="muted empty-state" style="padding:12px">На выбранную дату никто не записан</p>'}` : ''}
      </div></div>`;
    this.bindNav();
    document.getElementById('mealPrev').onclick = () => {
      nutritionDate = toDateString(nextWeekday(date, -1));
      this.pageNutrition();
    };
    document.getElementById('mealNext').onclick = () => {
      nutritionDate = toDateString(nextWeekday(date, 1));
      this.pageNutrition();
    };
    const toggle = document.getElementById('toggleMeal');
    if (toggle) toggle.onclick = async () => {
      const want = !sub?.isSubscribed;
      if (want && weekend) {
        showToast('В СБ и ВС записаться на питание нельзя');
        return;
      }
      const ok = await Db.setMealSubscription(dateStr, currentUser, want);
      if (!ok) { showToast('Не удалось изменить статус'); return; }
      showToast(want ? 'Вы записаны на питание' : 'Подписка отменена');
      this.pageNutrition();
    };
    const wp = document.getElementById('weeklyPlanBtn');
    if (wp) wp.onclick = async () => {
      const act = await showModal('Недельный автоплан', '<p>Записать или отписать на все учебные дни текущей недели (Пн–Пт)?</p>',
        [{ id: 'sub', label: '✅ Записать на Пн–Пт' }, { id: 'unsub', label: '❌ Отписать на Пн–Пт' }, { id: 'cancel', label: 'Отмена', class: 'btn-outline' }]);
      if (act === 'cancel') return;
      const ok = await Db.applyWeeklyMealPlan(currentUser, act === 'sub', mondayOfWeek(dateStr));
      showToast(`${act === 'sub' ? 'Записано' : 'Отписано'} на ${ok} из 5 дней (Пн–Пт)`);
      this.pageNutrition();
    };
    const sw = document.getElementById('weeklyAutoSwitch');
    if (sw) sw.onchange = async () => {
      const enabled = sw.checked;
      if (enabled) {
        const ok = await Db.applyWeeklyMealPlan(currentUser, true, mondayOf(new Date()));
        await Db.setMealAutoPlan(currentUser.id, true, currentWeekKey());
        currentUser.mealAutoPlanEnabled = true;
        currentUser.mealAutoPlanLastAppliedWeek = currentWeekKey();
        showToast(`Автоплан включен (${ok} дн.)`);
      } else {
        await Db.setMealAutoPlan(currentUser.id, false, '');
        currentUser.mealAutoPlanEnabled = false;
        currentUser.mealAutoPlanLastAppliedWeek = '';
        showToast('Автоплан выключен');
      }
    };
    const em = document.getElementById('exportMeal');
    if (em) em.onclick = () => {
      exportPrint(`Питание — ${dateStr}`, ['№', 'ФИО'], subs.map((s, i) => [i + 1, s.userName]));
    };
  },

  async pageClubs() {
    const gid = effectiveGroupId(currentUser);
    const clubs = await Db.getClubsForGroup(gid, isTeacher(currentUser) ? currentUser.id : null);
    const canManage = isTeacher(currentUser);
    document.getElementById('app').innerHTML = `<div class="page-scroll main-screen">
      ${UI.screenHeader('Кружки и секции', '🎯', 'purple menu-icon', 'purple', { subtitle: 'Внеучебная деятельность' })}
      <div class="page-content">${canManage ? '<button class="btn-primary" id="addClubBtn" style="margin-bottom:12px">+ Добавить</button>' : ''}<div class="tabs">
        <button class="tab active" data-club-tab="club">Кружки</button>
        <button class="tab" data-club-tab="section">Секции</button>
        <button class="tab" data-club-tab="elective">Факультативы</button></div>
        <div id="clubsBody">${UI.clubsList(clubs, 'club')}</div></div></div>`;
    this.bindNav();
    if (canManage) document.getElementById('addClubBtn').onclick = () => this.dialogClub(null, gid);
    const renderTab = (type) => {
      document.getElementById('clubsBody').innerHTML = UI.clubsList(clubs, type);
      document.querySelectorAll('[data-club-id]').forEach(el => el.onclick = () => {
        location.hash = `club?id=${el.dataset.clubId}`;
      });
    };
    document.querySelectorAll('[data-club-tab]').forEach(tab => tab.onclick = () => {
      document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
      tab.classList.add('active');
      renderTab(tab.dataset.clubTab);
    });
    renderTab('club');
  },

  async dialogClub(existing, gid) {
    const typeVal = (existing?.type || 'CLUB').toLowerCase();
    const act = await showModal(existing ? 'Изменить' : 'Новый кружок', `
      <label class="form-label">Название</label><input class="form-input" id="dName" value="${escapeHtml(existing?.name || '')}">
      <label class="form-label">Описание</label><input class="form-input" id="dDesc" value="${escapeHtml(existing?.description || '')}">
      <label class="form-label">Тип</label><select class="form-select" id="dType">
        <option value="club" ${typeVal === 'club' ? 'selected' : ''}>Кружок</option>
        <option value="section" ${typeVal === 'section' ? 'selected' : ''}>Секция</option>
        <option value="elective" ${typeVal === 'elective' ? 'selected' : ''}>Факультатив</option></select>
      <label class="form-label">Расписание</label><input class="form-input" id="dSchedule" value="${escapeHtml(existing?.schedule || '')}">
      <label class="form-label">Место</label><input class="form-input" id="dLocation" value="${escapeHtml(existing?.location || '')}">
      <label class="form-label">Макс. участников (0 — без лимита)</label><input class="form-input" id="dMax" type="number" value="${existing?.maxParticipants || 0}">`,
      [{ id: 'save', label: 'Сохранить' }, { id: 'cancel', label: 'Отмена', class: 'btn-outline' }]);
    if (act !== 'save') return;
    await Db.saveClub({
      id: existing?.id,
      name: document.getElementById('dName').value.trim(),
      description: document.getElementById('dDesc').value.trim(),
      type: document.getElementById('dType').value.toUpperCase(),
      schedule: document.getElementById('dSchedule').value.trim(),
      location: document.getElementById('dLocation').value.trim(),
      maxParticipants: parseInt(document.getElementById('dMax').value, 10) || 0,
      groupId: gid, teacherId: currentUser.id, teacherName: currentUser.fullName,
      participantIds: existing?.participantIds || [], isActive: true
    }, !existing);
    showToast('Сохранено');
    if (existing?.id) return this.pageClubDetail(existing.id);
    this.pageClubs();
  },

  async pageClubDetail(id) {
    const clubId = id || hashParams().get('id');
    document.getElementById('app').innerHTML = loadingHtml();
    const gid = effectiveGroupId(currentUser);
    const clubs = await Db.getClubsForGroup(gid, isTeacher(currentUser) ? currentUser.id : null);
    const club = clubs.find(c => c.id === clubId);
    if (!club) { showToast('Кружок не найден'); return this.go('clubs'); }
    location.hash = `club?id=${club.id}`;
    const joined = (club.participantIds || []).includes(currentUser.id);
    const isOwner = isTeacher(currentUser) && club.teacherId === currentUser.id;
    const max = club.maxParticipants || 0;
    const count = (club.participantIds || []).length;
    const canJoin = !isTeacher(currentUser) && !joined && (max === 0 || count < max);
    const typeLabel = { CLUB: 'Кружок', SECTION: 'Секция', ELECTIVE: 'Факультатив' }[(club.type || 'CLUB').toUpperCase()] || club.type;
    document.getElementById('app').innerHTML = UI.pageHeader(club.name, typeLabel, '🎯', 'purple menu-icon', '') +
      `<div class="page-content">
        <div class="list-card">
          <div class="list-card-body">${escapeHtml(club.description || 'Описание не заполнено')}</div>
          <div class="list-card-meta">${escapeHtml(club.schedule || '—')} · ${escapeHtml(club.location || '—')} · ${escapeHtml(club.teacherName || '—')}</div>
          <div class="list-card-meta">Участников: ${count}${max ? '/' + max : ''}</div>
        </div>
        ${isOwner ? `<div class="toolbar">
          <button class="btn-sm btn-primary" id="editClub">Изменить</button>
          <button class="btn-sm" id="manageParts">Участники</button>
          <button class="btn-sm btn-danger" id="delClub">Удалить</button></div>` : ''}
        ${canJoin ? `<button class="btn-primary" id="joinClub">Записаться</button>` : ''}
        ${joined && !isTeacher(currentUser) ? `<button class="btn-outline" id="joinClub">Отписаться</button>` : ''}
      </div>`;
    this.bindNav();
    if (isOwner) {
      document.getElementById('editClub').onclick = () => this.dialogClub(club, gid);
      document.getElementById('delClub').onclick = async () => {
        if (!confirm('Удалить кружок?')) return;
        await Db.deleteClub(club.id);
        showToast('Удалено');
        this.go('clubs');
      };
      document.getElementById('manageParts').onclick = async () => {
        const students = await Db.getGroupStudents(groupName(currentUser));
        const enrolled = new Set(club.participantIds || []);
        const checks = students.map(s =>
          `<label style="display:block;margin:6px 0"><input type="checkbox" class="club-part" value="${s.id}" ${enrolled.has(s.id) ? 'checked' : ''}> ${escapeHtml(s.fullName)}</label>`
        ).join('');
        const act = await showModal('Участники кружка', `<div style="max-height:240px;overflow:auto">${checks || '<p class="muted">Нет учащихся в группе</p>'}</div>`,
          [{ id: 'save', label: 'Сохранить' }, { id: 'cancel', label: 'Отмена', class: 'btn-outline' }]);
        if (act !== 'save') return;
        const selected = new Set([...document.querySelectorAll('.club-part:checked')].map(c => c.value));
        const maxP = club.maxParticipants || 0;
        if (maxP > 0 && selected.size > maxP) {
          showToast(`Максимум участников: ${maxP}`);
          return;
        }
        for (const s of students) {
          const was = enrolled.has(s.id);
          const now = selected.has(s.id);
          if (was !== now) await Db.toggleClubParticipant(club.id, s.id, now);
        }
        showToast('Список участников обновлён');
        this.pageClubDetail(club.id);
      };
    }
    const jb = document.getElementById('joinClub');
    if (jb) jb.onclick = async () => {
      await Db.toggleClubParticipant(club.id, currentUser.id, !joined);
      showToast(joined ? 'Вы отписаны' : 'Вы записаны');
      this.pageClubDetail(club.id);
    };
  },

  async pageNotifications() {
    document.getElementById('app').innerHTML = `<div class="page-scroll main-screen">
      ${UI.screenHeader('Уведомления', '🔔', 'blue menu-icon', 'blue', { subtitle: 'Все ваши уведомления' })}
      <div class="page-content"><div class="toolbar"><button class="btn-sm" id="markAllRead">Прочитать все</button>
      <button class="btn-sm btn-danger" id="delAll">Удалить все</button></div><div id="notifBody">${loadingHtml()}</div></div></div>`;
    this.bindNav();
    const render = (list) => {
      document.getElementById('notifBody').innerHTML = UI.notificationsList(list);
      document.querySelectorAll('[data-notif-id]').forEach(el => el.onclick = async () => {
        await Db.markNotificationRead(el.dataset.notifId);
        el.classList.remove('unread'); el.classList.add('read');
      });
    };
    if (notifUnsub) notifUnsub();
    notifUnsub = Db.subscribeNotifications(currentUser.id, render);
    document.getElementById('markAllRead').onclick = async () => { await Db.markAllNotificationsRead(currentUser.id); };
    document.getElementById('delAll').onclick = async () => {
      if (!confirm('Удалить все уведомления?')) return;
      await Db.deleteAllNotifications(currentUser.id);
    };
  },

  async pageChat() {
    const room = await Db.ensureChatRoom(currentUser);
    if (!room) {
      document.getElementById('app').innerHTML = UI.pageHeader('Чат', '', '💬', 'blue menu-icon', 'blue') +
        '<div class="empty-state"><span>💬</span>Группа не указана</div>';
      return this.bindNav();
    }
    let stickers = [];
    try {
      const r = await fetch('assets/stickers/stickers_catalog.json');
      const j = await r.json();
      stickers = (j.static || []).map(s => ({ id: s.id, path: s.path.replace('stickers/', 'assets/stickers/') }));
    } catch (e) {}
    document.getElementById('app').innerHTML = `<div class="chat-page">
      ${UI.pageHeader('Чат', room.title, '💬', 'blue menu-icon', 'blue')}
      <div id="pinnedBar" class="pinned-bar hidden"></div>
      <div class="chat-messages" id="chatMessages"></div>
      <div id="stickerPanel" class="sticker-panel hidden">${stickers.map(s =>
        `<img src="${s.path}" data-sticker="${s.id}" class="sticker-thumb" alt="">`).join('')}</div>
      <div class="chat-input-bar">
        <button type="button" id="stickerBtn" class="chat-icon-btn">🎨</button>
        <input type="text" id="chatInput" placeholder="Сообщение..." maxlength="2000">
        <button type="button" id="chatSend">➤</button>
      </div></div>`;
    this.bindNav();
    const msgEl = document.getElementById('chatMessages');
    const pinBar = document.getElementById('pinnedBar');
    let allMsgs = [];
    let chatSearchText = '';
    let chatSearchDate = '';
    const renderMessages = (msgs) => {
      allMsgs = msgs;
      let filtered = msgs;
      if (chatSearchText) {
        const q = chatSearchText.toLowerCase();
        filtered = filtered.filter(m => (m.text || '').toLowerCase().includes(q));
      }
      if (chatSearchDate) {
        filtered = filtered.filter(m => {
          const d = m.createdAt instanceof Date ? m.createdAt : new Date(m.createdAt);
          return toDateString(d) === chatSearchDate;
        });
      }
      if (!filtered.length) {
        msgEl.innerHTML = `<div class="empty-state" style="padding:24px"><span>💬</span>${msgs.length ? 'Ничего не найдено' : 'Начните переписку'}</div>`;
        return;
      }
      let lastDay = '';
      msgEl.innerHTML = filtered.map(m => {
        const mine = m.senderId === currentUser.id;
        const body = m.stickerId ? `<img src="assets/stickers/static/${m.stickerId}.png" class="chat-sticker" alt="">` : escapeHtml(m.text);
        const d = m.createdAt instanceof Date ? m.createdAt : new Date(m.createdAt);
        const dayKey = toDateString(d);
        let header = '';
        if (dayKey !== lastDay) {
          lastDay = dayKey;
          header = `<div class="chat-date-header">${escapeHtml(formatChatDayLabel(d))}</div>`;
        }
        return `${header}<div class="chat-bubble ${mine ? 'mine' : 'other'}" data-msg-id="${m.id}" data-sender="${m.senderId}">
          ${!mine ? `<div class="chat-sender">${escapeHtml(m.senderName)}</div>` : ''}${body}
          <div class="chat-time">${formatTime(d)}</div></div>`;
      }).join('');
      msgEl.scrollTop = msgEl.scrollHeight;
      bindMessageActions(filtered);
    };
    const bindMessageActions = (msgs) => {
      msgEl.querySelectorAll('.chat-bubble').forEach(b => {
        b.oncontextmenu = async e => {
          e.preventDefault();
          const mid = b.dataset.msgId;
          const msg = msgs.find(x => x.id === mid);
          if (!msg) return;
          const canDel = msg.senderId === currentUser.id || isTeacher(currentUser) || isHeadman(currentUser);
          const canPin = isTeacher(currentUser) || isHeadman(currentUser);
          const canEdit = msg.senderId === currentUser.id && !msg.stickerId;
          const btns = [{ id: 'cancel', label: 'Отмена', class: 'btn-outline' }];
          if (canEdit) btns.unshift({ id: 'edit', label: 'Изменить' });
          if (canPin) btns.unshift({ id: 'pin', label: 'Закрепить' });
          if (canDel) btns.unshift({ id: 'del', label: 'Удалить', class: 'btn-outline' });
          const act = await showModal('Сообщение', escapeHtml(msg.text || 'Стикер'), btns);
          if (act === 'del' && canDel) await Db.deleteChatMessage(mid);
          if (act === 'pin' && canPin) await Db.pinChatMessage(room.id, msg, currentUser.fullName);
          if (act === 'edit' && canEdit) {
            const editAct = await showModal('Изменить сообщение',
              `<textarea class="form-input" id="editMsgText" rows="3" style="resize:vertical">${escapeHtml(msg.text)}</textarea>`,
              [{ id: 'save', label: 'Сохранить' }, { id: 'cancel', label: 'Отмена', class: 'btn-outline' }]);
            if (editAct === 'save') {
              const t = document.getElementById('editMsgText')?.value;
              if (t?.trim()) await Db.updateChatMessage(mid, t);
            }
          }
        };
      });
    };
    roomUnsub = Db.subscribeRoom(room.id, r => {
      if (r.pinnedMessageText) {
        pinBar.classList.remove('hidden');
        pinBar.innerHTML = `<strong>📌 ${escapeHtml(r.pinnedByName || '')}:</strong> ${escapeHtml(r.pinnedMessageText)}`;
      } else pinBar.classList.add('hidden');
    });
    chatUnsub = Db.subscribeMessages(room.id, renderMessages);
    const send = async () => {
      const t = document.getElementById('chatInput').value;
      if (!t.trim()) return;
      await Db.sendChatMessage(room.id, currentUser, t);
      document.getElementById('chatInput').value = '';
    };
    document.getElementById('chatSend').onclick = send;
    document.getElementById('chatInput').onkeydown = e => { if (e.key === 'Enter') send(); };
    document.getElementById('stickerBtn').onclick = () => document.getElementById('stickerPanel').classList.toggle('hidden');
    document.querySelectorAll('[data-sticker]').forEach(img => img.onclick = async () => {
      await Db.sendChatSticker(room.id, currentUser, img.dataset.sticker);
      document.getElementById('stickerPanel').classList.add('hidden');
    });
    if (isTeacher(currentUser) || isHeadman(currentUser)) {
      const actions = document.getElementById('headerActions');
      if (actions) actions.innerHTML = '<button class="btn-sm" id="searchChat">🔍</button><button class="btn-sm" id="clearChat">Очистить</button><button class="btn-sm" id="unpinChat">Открепить</button>';
      document.getElementById('searchChat').onclick = async () => {
        const act = await showModal('Поиск в чате', `
          <label class="form-label">Текст</label><input class="form-input" id="chatSearchText" value="${escapeHtml(chatSearchText)}">
          <label class="form-label">Дата (ДД.ММ.ГГГГ)</label><input class="form-input" id="chatSearchDate" value="${escapeHtml(chatSearchDate)}" placeholder="Необязательно">`,
          [{ id: 'apply', label: 'Найти' }, { id: 'reset', label: 'Сброс', class: 'btn-outline' }, { id: 'cancel', label: 'Отмена', class: 'btn-outline' }]);
        if (act === 'cancel') return;
        if (act === 'reset') { chatSearchText = ''; chatSearchDate = ''; }
        else {
          chatSearchText = document.getElementById('chatSearchText')?.value.trim() || '';
          chatSearchDate = document.getElementById('chatSearchDate')?.value.trim() || '';
        }
        renderMessages(allMsgs);
      };
      document.getElementById('clearChat').onclick = async () => {
        if (confirm('Очистить чат?')) { await Db.clearChatMessages(room.id); showToast('Чат очищен'); }
      };
      document.getElementById('unpinChat').onclick = async () => {
        await Db.unpinChatMessage(room.id); showToast('Откреплено');
      };
    }
  },

  async pageProfile() {
    let stats = null;
    if (isStudent(currentUser) || isHeadman(currentUser)) {
      try {
        const sem = await resolveCurrentSemester(currentUser);
        let grades = (await Db.getStudentGrades(currentUser.id)).filter(isVisibleGrade);
        let absences = await Db.getStudentAbsences(currentUser.id);
        if (sem) {
          grades = filterBySemester(grades, sem);
          absences = filterBySemester(absences, sem);
        }
        const forAvg = grades.filter(g => g.value >= 1 && g.value <= 10);
        const totalGrades = grades.length;
        stats = {
          avg: totalGrades > 0 && forAvg.length
            ? (forAvg.reduce((s, g) => s + g.value, 0) / forAvg.length).toFixed(1) : '—',
          gradesCount: totalGrades,
          absenceHours: absences.reduce((s, a) => s + (a.hours || 0), 0)
        };
      } catch (e) {
        console.error(e);
      }
    }
    document.getElementById('app').innerHTML = UI.profile(currentUser, stats);
    this.bindNav();
    const logoutBtn = document.getElementById('logoutBtn');
    if (logoutBtn) logoutBtn.onclick = async () => {
      await Db.auth.signOut();
      backStack = ['main'];
      showToast('Выход');
    };
    const resetBtn = document.getElementById('resetPwdBtn');
    if (resetBtn) resetBtn.onclick = async () => {
      await Db.auth.sendPasswordResetEmail(currentUser.email);
      showToast(`Письмо для смены пароля отправлено на ${currentUser.email}`);
    };
  },

  async pageEditProfile() {
    if (!isStudent(currentUser) && !isHeadman(currentUser)) {
      showToast('Нет доступа');
      return this.go('profile');
    }
    const u = currentUser;
    document.getElementById('app').innerHTML = UI.pageHeader('Личная информация', 'Заполните ваши данные', '👤', 'gray', '') +
      `<div class="page-content">
        <label class="form-label">Адрес</label><input class="form-input" id="address" value="${escapeHtml(u.address)}">
        <label class="form-label">Дата рождения</label><input class="form-input" id="birthDate" value="${escapeHtml(u.birthDate)}" placeholder="ДД.ММ.ГГГГ">
        <label class="form-label">Телефон</label><input class="form-input" id="phone" value="${escapeHtml(u.phone)}">
        <label class="form-label">Родитель 1</label><input class="form-input" id="parentName" value="${escapeHtml(u.parentName)}">
        <label class="form-label">Телефон родителя</label><input class="form-input" id="parentPhone" value="${escapeHtml(u.parentPhone)}">
        <label class="form-label">Родитель 2</label><input class="form-input" id="parentName2" value="${escapeHtml(u.parentName2 || '')}">
        <label class="form-label">Телефон родителя 2</label><input class="form-input" id="parentPhone2" value="${escapeHtml(u.parentPhone2 || '')}">
        <label class="form-label">Финансирование</label>
        <select class="form-select" id="fundingType"><option ${!u.fundingType ? 'selected' : ''}>Не выбрано</option>
          <option ${u.fundingType === 'Бюджет' ? 'selected' : ''}>Бюджет</option>
          <option ${u.fundingType === 'Платник' ? 'selected' : ''}>Платник</option></select>
        <label class="form-label"><input type="checkbox" id="livesInDormitory" ${u.livesInDormitory ? 'checked' : ''}> Общежитие</label>
        <label class="form-label"><input type="checkbox" id="isDisabled" ${u.isDisabled ? 'checked' : ''}> Инвалидность</label>
        <label class="form-label"><input type="checkbox" id="isLargeFamily" ${u.isLargeFamily ? 'checked' : ''}> Многодетная семья</label>
        <label class="form-label"><input type="checkbox" id="isLowIncome" ${u.isLowIncome ? 'checked' : ''}> Малообеспеченная</label>
        <label class="form-label"><input type="checkbox" id="isOrphan" ${u.isOrphan ? 'checked' : ''}> Сирота</label>
        <label class="form-label"><input type="checkbox" id="isNonResident" ${u.isNonResident ? 'checked' : ''}> Иногородний</label>
        <button class="btn-primary" id="saveProfile">💾 Сохранить</button>
        <button class="btn-outline btn-danger-outline" id="deletePersonal">🗑️ Удалить всю информацию</button></div>`;
    this.bindNav();
    document.getElementById('saveProfile').onclick = async () => {
      await Db.updatePersonalInfo(u.id, {
        address: document.getElementById('address').value.trim(),
        birthDate: document.getElementById('birthDate').value.trim(),
        phone: document.getElementById('phone').value.trim(),
        parentName: document.getElementById('parentName').value.trim(),
        parentPhone: document.getElementById('parentPhone').value.trim(),
        parentName2: document.getElementById('parentName2').value.trim(),
        parentPhone2: document.getElementById('parentPhone2').value.trim(),
        fundingType: document.getElementById('fundingType').value === 'Не выбрано' ? '' : document.getElementById('fundingType').value,
        livesInDormitory: document.getElementById('livesInDormitory').checked,
        isDisabled: document.getElementById('isDisabled').checked,
        isLargeFamily: document.getElementById('isLargeFamily').checked,
        isLowIncome: document.getElementById('isLowIncome').checked,
        isOrphan: document.getElementById('isOrphan').checked,
        isNonResident: document.getElementById('isNonResident').checked
      });
      currentUser = await Db.fetchUser(u.id);
      window.__currentUser = currentUser;
      showToast('Сохранено');
      this.go('profile');
    };
    document.getElementById('deletePersonal').onclick = async () => {
      if (!confirm('Удалить все личные данные?')) return;
      await Db.deletePersonalData(u.id);
      currentUser = await Db.fetchUser(u.id);
      window.__currentUser = currentUser;
      showToast('Данные удалены');
      this.go('profile');
    };
  },

  pageTeacherHub() {
    document.getElementById('app').innerHTML = UI.teacherHub(currentUser);
    this.bindNav();
  },

  pageHeadmanHub() {
    document.getElementById('app').innerHTML = UI.headmanHub(currentUser);
    this.bindNav();
  },

  pageAdminHub() {
    const accessDenied = !isTeacher(currentUser);
    document.getElementById('app').innerHTML = UI.adminHub(accessDenied);
    this.bindNav();
    if (accessDenied) showToast('Нет доступа');
  },

  async dialogGrade(st, subject, date, existing, semesters) {
    let semNum = existing?.semester || 1;
    if (semesters?.length) {
      const d = parseDate(date);
      const sem = semesters.find(s => {
        const start = parseDate(s.startDate), end = parseDate(s.endDate);
        return start && end && d && d >= start && d <= end;
      });
      if (sem) semNum = semesterNumber(sem.name) || semNum;
    }
    const valOpts = GRADE_VALUES.map(v => `<option value="${v}" ${existing?.value === v ? 'selected' : ''}>${v}</option>`).join('');
    const typeOpts = GRADE_TYPE_OPTIONS.map(t => `<option ${existing?.type === t ? 'selected' : ''}>${escapeHtml(t)}</option>`).join('');
    const btns = [{ id: 'save', label: 'Сохранить' }, { id: 'cancel', label: 'Отмена', class: 'btn-outline' }];
    if (existing) btns.splice(1, 0, { id: 'del', label: 'Удалить', class: 'btn-outline' }, { id: 'absence', label: 'Неявка (Н)', class: 'btn-outline' });
    const act = await showModal(`Отметка — ${st.fullName}`, `
      <div class="list-card-meta" style="margin-bottom:12px">${escapeHtml(subject)} · ${escapeHtml(date)}</div>
      <label class="form-label">Балл</label><select class="form-select" id="dValue">${valOpts}<option value="0">Н (неявка)</option></select>
      <label class="form-label">Тип</label><select class="form-select" id="dType">${typeOpts}</select>`,
      btns);
    if (act === 'cancel') return null;
    if (act === 'del' && existing) { await Db.deleteGrade(existing.id); return 'deleted'; }
    if (act === 'absence') {
      const grade = {
        id: existing?.id, studentId: st.id, studentName: st.fullName, subject, date,
        teacherId: currentUser.id, teacherName: currentUser.fullName,
        value: 0, type: 'Неявка', semester: semNum, notifyUserId: st.id
      };
      await Db.saveGrade(grade, !existing);
      return 'Н';
    }
    if (act !== 'save') return null;
    const val = parseInt(document.getElementById('dValue').value, 10);
    const type = document.getElementById('dType').value;
    const grade = {
      id: existing?.id, studentId: st.id, studentName: st.fullName, subject, date,
      teacherId: currentUser.id, teacherName: currentUser.fullName,
      value: val, type: val === 0 ? 'Неявка' : type, semester: semNum, notifyUserId: st.id
    };
    await Db.saveGrade(grade, !existing);
    return displayGradeCell(grade);
  },

  async pageJournal() {
    const gn = groupName(currentUser);
    const gid = effectiveGroupId(currentUser);
    const [students, subjects, semesters] = await Promise.all([
      Db.getGroupStudents(gn), Db.getSubjects(gid, gn), Db.getSemesters(gid, gn)
    ]);
    const subOpts = subjects.map(s => `<option value="${escapeHtml(s.name)}">${escapeHtml(s.name)}</option>`).join('');
    const semOpts = `<option value="">Все семестры</option>` + semesters.map(s =>
      `<option value="${s.id}">${escapeHtml(s.name)}</option>`).join('');
    document.getElementById('app').innerHTML = UI.pageHeader('Журнал отметок', gn, '📊', 'green menu-icon', 'green') +
      `<div class="page-content">${UI.journalToolbar(formatWeekLabel(journalWeekStart), subOpts, semOpts)}
      <div id="journalBody" style="margin-top:16px">${loadingHtml()}</div></div>`;
    this.bindNav();
    document.getElementById('prevWeek').onclick = () => {
      journalWeekStart.setDate(journalWeekStart.getDate() - 7);
      this.pageJournal();
    };
    document.getElementById('nextWeek').onclick = () => {
      journalWeekStart.setDate(journalWeekStart.getDate() + 7);
      this.pageJournal();
    };
    const loadJournal = async () => {
      const subject = document.getElementById('jSubject').value;
      const semId = document.getElementById('jSemester').value;
      const sem = semesters.find(s => s.id === semId);
      let grades = (await Db.getTeacherGrades(currentUser.id)).filter(g => g.subject === subject);
      if (sem) grades = filterBySemester(grades, sem);
      const dates = weekDates(journalWeekStart);
      const map = {};
      grades.forEach(g => { map[`${g.studentId}_${g.date}`] = g; });
      const labels = await Db.getJournalLabels(currentUser.id, gn, subject, dates);
      document.getElementById('weekLabel').textContent = formatWeekLabel(journalWeekStart);
      document.getElementById('journalBody').innerHTML = students.length
        ? UI.journalGrid(students, dates, map, labels)
        : '<div class="empty-state">Нет учащихся в группе</div>';
      document.querySelectorAll('.journal-col-h').forEach(th => {
        th.onclick = async () => {
          const date = th.dataset.colDate;
          const cur = labels[date] || '';
          const idx = JOURNAL_COLUMN_TYPES.indexOf(cur);
          const next = JOURNAL_COLUMN_TYPES[(idx + 1) % JOURNAL_COLUMN_TYPES.length];
          await Db.setJournalLabel(currentUser.id, gn, subject, date, next);
          loadJournal();
        };
      });
      document.querySelectorAll('.journal-cell').forEach(cell => {
        cell.onclick = async () => {
          const st = students.find(s => s.id === cell.dataset.student);
          if (!st || !subject) return;
          const existing = map[`${st.id}_${cell.dataset.date}`];
          const result = await this.dialogGrade(st, subject, cell.dataset.date, existing, semesters);
          if (result === 'deleted') { cell.textContent = ''; showToast('Удалено'); return; }
          if (result) { cell.textContent = result; showToast('Сохранено'); }
        };
      });
      const exp = document.getElementById('exportJournal');
      if (exp) exp.onclick = () => {
        const rows = [['Учащийся', ...dates]];
        students.forEach(st => {
          rows.push([st.fullName, ...dates.map(d => {
            const g = map[`${st.id}_${d}`];
            return g ? displayGradeCell(g) : '';
          })]);
        });
        exportPrint(`journal_${subject}_${formatWeekLabel(journalWeekStart)}`, rows[0], rows.slice(1));
      };
    };
    document.getElementById('jSubject').onchange = loadJournal;
    document.getElementById('jSemester').onchange = loadJournal;
    if (subjects.length) loadJournal();
    else document.getElementById('journalBody').innerHTML = '<div class="empty-state">Добавьте предметы в справочниках</div>';
  },

  async pageStudents() {
    const gn = groupName(currentUser);
    const students = await Db.getGroupStudents(gn);
    document.getElementById('app').innerHTML = UI.pageHeader('Учащиеся группы', gn, '👥', 'blue menu-icon', 'blue') +
      `<div class="page-content"><div class="list-card-meta" style="margin-bottom:12px">Всего: ${students.length}</div>
      ${students.map(s => `<div class="list-card clickable" data-student-id="${s.id}">
        <div class="list-card-title">${escapeHtml(s.fullName)}</div>
        <div class="list-card-meta">${escapeHtml(s.email)} · ${roleBadgeLabel(s)}</div></div>`).join('') || '<div class="empty-state">Нет учащихся</div>'}</div>`;
    this.bindNav();
    document.querySelectorAll('[data-student-id]').forEach(el => {
      el.onclick = () => { location.hash = `student-info?id=${el.dataset.studentId}`; };
    });
  },

  async pageStudentInfo() {
    const id = hashParams().get('id');
    if (!id) return this.go('students');
    document.getElementById('app').innerHTML = loadingHtml();
    const student = await Db.fetchUser(id);
    if (!student) { showToast('Учащийся не найден'); return this.go('students'); }
    document.getElementById('app').innerHTML = UI.studentInfo(student);
    this.bindNav();
  },

  async pageStudentGrades() {
    const id = hashParams().get('id');
    if (!id) return this.go('students');
    const student = await Db.fetchUser(id);
    if (!student) return this.go('students');
    const gid = effectiveGroupId(currentUser);
    const semesters = await Db.getSemesters(gid);
    const grades = (await Db.getStudentGrades(id)).filter(isVisibleGrade);
    const fa = grades.filter(g => g.value >= 1 && g.value <= 10);
    const avg = fa.length ? (fa.reduce((s, g) => s + g.value, 0) / fa.length).toFixed(1) : '—';
    const canAdd = isTeacher(currentUser);
    document.getElementById('app').innerHTML = UI.pageHeader('Отметки', student.fullName, '📊', 'green menu-icon', 'green') +
      `<div class="page-content"><div class="quick-stats" style="padding:0 0 16px">
        <div class="stat-card"><div class="stat-value stat-emerald">${avg}</div><div class="stat-label">Ср. балл</div></div>
        <div class="stat-card"><div class="stat-value stat-purple">${grades.length}</div><div class="stat-label">Отметок</div></div>
      </div>${canAdd ? '<button class="btn-primary" id="addGradeBtn" style="margin-bottom:12px">+ Выставить отметку</button>' : ''}
      ${UI.gradesList(grades)}</div>`;
    this.bindNav();
    if (canAdd) document.getElementById('addGradeBtn').onclick = async () => {
      const subjects = await Db.getSubjects(gid);
      const subOpts = subjects.map(s => `<option>${escapeHtml(s.name)}</option>`).join('');
      const act = await showModal('Новая отметка', `
        <label class="form-label">Предмет</label><select class="form-select" id="gSubject">${subOpts || '<option>—</option>'}</select>
        <label class="form-label">Дата</label><input class="form-input" id="gDate" value="${toDateString(new Date())}">`,
        [{ id: 'next', label: 'Далее' }, { id: 'cancel', label: 'Отмена', class: 'btn-outline' }]);
      if (act !== 'next') return;
      const subject = document.getElementById('gSubject').value;
      const date = document.getElementById('gDate').value.trim();
      const result = await this.dialogGrade(student, subject, date, null, semesters);
      if (result) { showToast('Сохранено'); this.pageStudentGrades(); }
    };
  },

  async pageStatistics() {
    const gn = groupName(currentUser);
    const gid = effectiveGroupId(currentUser);
    const [students, semesters] = await Promise.all([Db.getGroupStudents(gn), Db.getSemesters(gid)]);
    if (!window._statSemester) window._statSemester = '';
    const semOpts = `<option value="">Все семестры</option>` + semesters.map(s =>
      `<option value="${s.id}" ${window._statSemester === s.id ? 'selected' : ''}>${escapeHtml(s.name)}</option>`).join('');
    const sem = semesters.find(s => s.id === window._statSemester);
    const gradesMap = await Db.getGradesForStudents(students.map(s => s.id));
    const statsMap = {};
    for (const st of students) {
      let grades = (gradesMap[st.id] || []).filter(isVisibleGrade);
      if (sem) grades = filterBySemester(grades, sem);
      const fa = grades.filter(g => g.value >= 1 && g.value <= 10);
      statsMap[st.id] = {
        avg: fa.length ? (fa.reduce((s, g) => s + g.value, 0) / fa.length).toFixed(1) : '—',
        count: grades.length
      };
    }
    const statTitle = isHeadman(currentUser) ? 'Статистика группы' : 'Статистика успеваемости';
    document.getElementById('app').innerHTML = `<div class="page-scroll main-screen">
      ${UI.screenHeader(statTitle, '📈', 'purple menu-icon', 'purple', { subtitle: gn, exportId: 'exportStats' })}
      <div class="page-content">
        <label class="form-label">Семестр</label><select class="form-select" id="statSemester">${semOpts}</select>
        <div class="section-title" style="margin-top:16px">👨‍🎓 Успеваемость учащихся</div>
        ${UI.statisticsList(students, statsMap)}
      </div></div>`;
    this.bindNav();
    document.getElementById('statSemester').onchange = () => {
      window._statSemester = document.getElementById('statSemester').value;
      this.pageStatistics();
    };
    document.getElementById('exportStats').onclick = () => {
      exportPrint(`${statTitle} — ${gn}`, ['ФИО', 'Средний балл', 'Отметок'],
        students.map(st => [st.fullName, statsMap[st.id]?.avg || '—', statsMap[st.id]?.count || 0]));
    };
  },

  async pageCuratorial() {
    const gn = groupName(currentUser);
    const students = await Db.getGroupStudents(gn);
    const studentsMap = Object.fromEntries(students.map(s => [s.id, s]));
    const all = (await Db.getScheduleForGroup(gn)).filter(i => i.type === 'CURATOR_HOUR' || i.type === 'INFO_HOUR');
    const stats = {
      group: gn,
      total: all.length,
      curatorial: all.filter(i => i.type === 'CURATOR_HOUR').length,
      info: all.filter(i => i.type === 'INFO_HOUR').length
    };
    let items = curatorialFilter === 'all' ? all : all.filter(i => i.type === curatorialFilter);
    items = filterCuratorialByDate(items, curatorialDateSingle, curatorialDateFrom, curatorialDateTo);
    document.getElementById('app').innerHTML = UI.curatorialPage(stats, items, curatorialFilter, curatorialDateLabel, studentsMap);
    this.bindNav();
    document.querySelectorAll('[data-filter]').forEach(chip => {
      chip.onclick = () => { curatorialFilter = chip.dataset.filter; this.pageCuratorial(); };
    });
    document.getElementById('curatorialDateFilter').onclick = async () => {
      const act = await showModal('Фильтр по дате', `
        <label class="form-label">Одна дата (ДД.ММ.ГГГГ)</label><input class="form-input" id="fSingle" value="${escapeHtml(curatorialDateSingle || '')}">
        <label class="form-label">С даты</label><input class="form-input" id="fFrom" value="${escapeHtml(curatorialDateFrom || '')}">
        <label class="form-label">По дату</label><input class="form-input" id="fTo" value="${escapeHtml(curatorialDateTo || '')}">`,
        [{ id: 'apply', label: 'Применить' }, { id: 'reset', label: 'Все даты', class: 'btn-outline' }, { id: 'cancel', label: 'Отмена', class: 'btn-outline' }]);
      if (act === 'cancel') return;
      if (act === 'reset') {
        curatorialDateSingle = curatorialDateFrom = curatorialDateTo = null;
        curatorialDateLabel = 'Все даты';
      } else {
        curatorialDateSingle = document.getElementById('fSingle')?.value.trim() || null;
        curatorialDateFrom = document.getElementById('fFrom')?.value.trim() || null;
        curatorialDateTo = document.getElementById('fTo')?.value.trim() || null;
        if (curatorialDateSingle) curatorialDateLabel = curatorialDateSingle;
        else if (curatorialDateFrom && curatorialDateTo) curatorialDateLabel = `${curatorialDateFrom} — ${curatorialDateTo}`;
        else curatorialDateLabel = 'Все даты';
      }
      this.pageCuratorial();
    };
    document.getElementById('addCuratorial').onclick = () => this.dialogSchedule(null, gn, toDateString(new Date()), 'curatorial');
    document.getElementById('addInfo').onclick = async () => {
      const item = { type: 'INFO_HOUR' };
      await this.dialogSchedule(item, gn, toDateString(new Date()), 'curatorial');
    };
    document.querySelectorAll('[data-schedule-id]').forEach(el => {
      el.onclick = () => {
        const item = items.find(x => x.id === el.dataset.scheduleId) || all.find(x => x.id === el.dataset.scheduleId);
        if (item && canModify(currentUser, item.createdBy, item.createdByRole)) this.dialogSchedule(item, gn, item.date, 'curatorial');
      };
    });
  },

  async pageAdminCatalog() {
    if (!isTeacher(currentUser)) { showToast('Нет доступа'); return this.go('main'); }
    const gid = effectiveGroupId(currentUser);
    const gn = groupName(currentUser);
    if (!gid || !gn) {
      showToast('Для куратора не задана группа');
      return this.go('admin');
    }
    document.getElementById('app').innerHTML = loadingHtml();
    const catalog = await Db.loadAdminCatalog(gid, gn);
    window.__adminCatalog = catalog;
    if (!window._catalogSelectedTeacher || !catalog.teachers.some(t => t.id === window._catalogSelectedTeacher)) {
      window._catalogSelectedTeacher = catalog.teachers[0]?.id || '';
    }
    document.getElementById('app').innerHTML = UI.adminCatalog(catalog, window._catalogSelectedTeacher);
    this.bindNav();
    this.bindAdminCatalogHandlers(catalog, gid, gn);
  },

  bindAdminCatalogHandlers(catalog, gid, gn) {
    const parseLimit = (elId) => {
      const t = (document.getElementById(elId)?.value || '').trim();
      if (!t) return null;
      const n = parseInt(t, 10);
      return isNaN(n) ? NaN : n;
    };
    const refresh = () => this.pageAdminCatalog();

    document.getElementById('saveLimitsBtn').onclick = async () => {
      const teacherLimit = parseLimit('limTeachers');
      const studentLimit = parseLimit('limStudents');
      const headmanLimit = parseLimit('limHeadmen');
      if ([teacherLimit, studentLimit, headmanLimit].some(v => v !== null && (isNaN(v) || v < 0))) {
        showToast('Лимиты не могут быть отрицательными');
        return;
      }
      const ok = await Db.saveGroupLimits(gid, {
        teacherLimit: teacherLimit ?? null,
        studentLimit: studentLimit ?? null,
        headmanLimit: headmanLimit ?? null
      });
      showToast(ok ? 'Лимит установлен' : 'Не удалось сохранить лимиты');
      if (ok) refresh();
    };

    document.getElementById('addSubjectBtn').onclick = async () => {
      const name = document.getElementById('subjectInput')?.value.trim();
      if (!name) { showToast('Введите название предмета'); return; }
      const ok = await Db.addSubject(name, gid, gn);
      if (ok) { showToast('Предмет добавлен'); refresh(); }
      else showToast(catalog.subjects.some(s => s.name.toLowerCase() === name.toLowerCase())
        ? 'Такой предмет уже есть' : 'Не удалось добавить предмет');
    };

    const refreshAssignSemesters = (forUnassign = false) => {
      const subName = document.getElementById('assignSubject')?.value;
      const subject = catalog.subjects.find(s => s.name === subName);
      const semSel = document.getElementById('assignSemester');
      if (!semSel) return;
      let list = catalog.semesters;
      if (forUnassign && subject) {
        list = catalog.semesters.filter(s => (subject.semesterIds || []).includes(s.id));
      }
      semSel.innerHTML = list.map(s =>
        `<option value="${s.id}">${escapeHtml(semesterLabel(s))}</option>`).join('') || '<option value="">—</option>';
    };
    document.getElementById('assignSubject')?.addEventListener('change', () => refreshAssignSemesters(false));
    refreshAssignSemesters(false);

    document.getElementById('assignSemesterBtn').onclick = async () => {
      const subName = document.getElementById('assignSubject')?.value;
      const semId = document.getElementById('assignSemester')?.value;
      if (!subName) { showToast('Выберите предмет'); return; }
      if (!semId) { showToast('Выберите семестр'); return; }
      const subject = catalog.subjects.find(s => s.name === subName);
      const semester = catalog.semesters.find(s => s.id === semId);
      if (!subject || !semester) { showToast('Предмет или семестр не найден'); return; }
      if ((subject.semesterIds || []).includes(semester.id)) {
        showToast('Этот семестр уже назначен предмету');
        return;
      }
      const res = await Db.assignSubjectSemester(subName, gid, semester);
      if (res === 'SUCCESS') { showToast('Семестр назначен'); refresh(); }
      else if (res === 'DUPLICATE') showToast('Этот семестр уже назначен предмету');
      else showToast('Не удалось назначить семестр');
    };

    document.getElementById('unassignSemesterBtn').onclick = async () => {
      refreshAssignSemesters(true);
      const subName = document.getElementById('assignSubject')?.value;
      const semId = document.getElementById('assignSemester')?.value;
      if (!subName) { showToast('Выберите предмет'); return; }
      if (!semId) { showToast('Выберите семестр для отвязки'); return; }
      const subject = catalog.subjects.find(s => s.name === subName);
      if (!subject || !(subject.semesterIds || []).includes(semId)) {
        showToast('Этот семестр не привязан к предмету');
        return;
      }
      const res = await Db.unassignSubjectSemester(subName, gid, semId);
      if (res === 'SUCCESS') { showToast('Семестр отвязан'); refresh(); }
      else showToast('Не удалось отвязать семестр');
    };

    document.getElementById('addSemesterBtn').onclick = async () => {
      const numRaw = document.getElementById('semesterNumber')?.value.trim();
      const start = document.getElementById('semesterStart')?.value.trim();
      const end = document.getElementById('semesterEnd')?.value.trim();
      if (!numRaw || !start || !end) { showToast('Заполните все поля семестра'); return; }
      const num = parseInt(numRaw, 10);
      if (!num || num <= 0) { showToast('Введите корректный номер семестра'); return; }
      const startD = parseDate(start), endD = parseDate(end);
      if (!startD || !endD) { showToast('Некорректный формат даты'); return; }
      if (startD > endD) { showToast('Дата начала не может быть позже даты конца'); return; }
      const ok = await Db.addSemester(`${num} семестр`, gid, gn, start, end);
      if (ok) { showToast('Семестр добавлен'); refresh(); }
      else showToast(catalog.semesters.some(s => s.name === `${num} семестр`)
        ? 'Такой семестр уже есть для группы' : 'Не удалось добавить семестр');
    };

    document.getElementById('addTeacherBtn').onclick = async () => {
      const name = document.getElementById('teacherNameInput')?.value.trim();
      if (!name) { showToast('Введите ФИО преподавателя'); return; }
      if (catalog.teachers.some(t => t.fullName.toLowerCase() === name.toLowerCase())) {
        showToast('Такой преподаватель уже есть');
        return;
      }
      const ok = await Db.addCatalogTeacher(name, []);
      if (ok) { showToast('Преподаватель добавлен'); refresh(); }
      else showToast('Не удалось добавить преподавателя');
    };

    document.getElementById('teacherSelect')?.addEventListener('change', e => {
      window._catalogSelectedTeacher = e.target.value;
      refresh();
    });

    document.getElementById('addTeacherBindingBtn').onclick = async () => {
      const teacherId = document.getElementById('teacherSelect')?.value;
      const subName = document.getElementById('teacherSubjectSelect')?.value;
      if (!teacherId) { showToast('Выберите преподавателя'); return; }
      if (!subName) { showToast('Выберите предмет'); return; }
      const teacher = catalog.teachers.find(t => t.id === teacherId);
      const subject = catalog.subjects.find(s => s.name === subName);
      if (!teacher || !subject) { showToast('Преподаватель или предмет не найден'); return; }
      const sid = subjectDocumentId(subject.name, subject.groupId || gid);
      if ((teacher.subjectIds || []).includes(sid)) {
        showToast('Эта привязка уже добавлена');
        return;
      }
      const ok = await Db.updateTeachersByName(catalog.rawTeachers, teacher.fullName, ids => {
        if (ids.includes(sid)) return ids;
        return [...ids, sid];
      });
      if (ok) { showToast('Привязка добавлена'); refresh(); }
      else showToast('Не удалось добавить привязку');
    };

    document.querySelectorAll('[data-unbind-teacher]').forEach(btn => btn.onclick = async () => {
      const teacher = catalog.teachers.find(t => t.id === btn.dataset.unbindTeacher);
      const sid = btn.dataset.unbindSubject;
      if (!teacher || !sid) return;
      if (!confirm(`Снять назначение предмета у ${teacher.fullName}?`)) return;
      const ok = await Db.updateTeachersByName(catalog.rawTeachers, teacher.fullName, ids =>
        ids.filter(x => x !== sid)
      );
      if (ok) { showToast('Привязка удалена'); refresh(); }
      else showToast('Не удалось удалить привязку');
    });

    document.querySelectorAll('[data-edit-subject]').forEach(btn => btn.onclick = async () => {
      const oldName = btn.dataset.editSubject;
      const subject = catalog.subjects.find(s => s.name === oldName);
      if (!subject) return;
      const act = await showModal('Редактировать предмет',
        `<input class="form-input" id="dName" value="${escapeHtml(subject.name)}">`,
        [{ id: 'save', label: 'Сохранить' }, { id: 'cancel', label: 'Отмена', class: 'btn-outline' }]);
      const newName = document.getElementById('dName')?.value.trim();
      if (act !== 'save' || !newName || newName === oldName) return;
      const ok = await Db.updateSubject(oldName, gid, newName);
      if (ok) { showToast('Сохранено'); refresh(); }
      else showToast('Не удалось сохранить');
    });

    document.querySelectorAll('[data-rm-subject]').forEach(btn => btn.onclick = async () => {
      const name = btn.dataset.rmSubject;
      if (!confirm(`Удалить предмет «${name}»?`)) return;
      await Db.removeSubject(name, gid);
      showToast('Удалено');
      refresh();
    });

    document.querySelectorAll('[data-edit-semester]').forEach(btn => btn.onclick = async () => {
      const semester = catalog.semesters.find(s => s.id === btn.dataset.editSemester);
      if (!semester) return;
      const act = await showModal('Редактировать семестр',
        `<p class="catalog-hint">Группа: ${escapeHtml(gn)}</p>
        <label class="form-label">Номер семестра</label><input class="form-input" id="dNum" type="number" value="${semesterNumberFromName(semester.name) || ''}">
        <label class="form-label">Дата начала</label><input class="form-input" id="dStart" value="${escapeHtml(semester.startDate)}">
        <label class="form-label">Дата конца</label><input class="form-input" id="dEnd" value="${escapeHtml(semester.endDate)}">`,
        [{ id: 'save', label: 'Сохранить' }, { id: 'cancel', label: 'Отмена', class: 'btn-outline' }]);
      if (act !== 'save') return;
      const num = parseInt(document.getElementById('dNum')?.value, 10);
      const start = document.getElementById('dStart')?.value.trim();
      const end = document.getElementById('dEnd')?.value.trim();
      if (!num || num <= 0 || !start || !end) { showToast('Заполните все поля'); return; }
      const startD = parseDate(start), endD = parseDate(end);
      if (!startD || !endD) { showToast('Некорректный формат даты'); return; }
      if (startD > endD) { showToast('Дата начала не может быть позже даты конца'); return; }
      const ok = await Db.updateSemester(semester, num, start, end);
      if (ok) { showToast('Семестр обновлён'); refresh(); }
      else showToast('Не удалось сохранить. Возможно, такой семестр уже есть для группы.');
    });

    document.querySelectorAll('[data-rm-semester]').forEach(btn => btn.onclick = async () => {
      const semester = catalog.semesters.find(s => s.id === btn.dataset.rmSemester);
      if (!semester) return;
      if (!confirm(`Удалить семестр «${semester.name}» (${semester.startDate} — ${semester.endDate})?`)) return;
      await Db.removeSemester(semester.id);
      showToast('Удалено');
      refresh();
    });

    document.querySelectorAll('[data-edit-teacher]').forEach(btn => btn.onclick = async () => {
      const teacher = catalog.teachers.find(t => t.id === btn.dataset.editTeacher);
      if (!teacher) return;
      const oldName = teacher.fullName;
      const act = await showModal('Редактировать преподавателя',
        `<input class="form-input" id="dName" value="${escapeHtml(teacher.fullName)}">`,
        [{ id: 'save', label: 'Сохранить' }, { id: 'cancel', label: 'Отмена', class: 'btn-outline' }]);
      const newName = document.getElementById('dName')?.value.trim();
      if (act !== 'save' || !newName || newName.toLowerCase() === oldName.toLowerCase()) return;
      const dupes = catalog.rawTeachers.filter(t => t.fullName.trim().toLowerCase() === oldName.trim().toLowerCase());
      const dupeIds = new Set(dupes.map(d => d.id));
      const conflicting = catalog.rawTeachers.some(t =>
        !dupeIds.has(t.id) && t.fullName.trim().toLowerCase() === newName.toLowerCase()
      );
      if (conflicting) { showToast('Уже есть преподаватель с таким ФИО'); return; }
      const mergedIds = [...new Set(dupes.flatMap(d => d.subjectIds || []))];
      const primaryId = dupes.sort((a, b) => a.id.localeCompare(b.id))[0]?.id || teacher.id;
      const ok = await Db.updateCatalogTeacher(primaryId, newName, mergedIds);
      if (!ok) { showToast('Не удалось сохранить'); return; }
      for (const d of dupes) {
        if (d.id !== primaryId) await Db.removeCatalogTeacher(d.id);
      }
      showToast('Сохранено');
      refresh();
    });

    document.querySelectorAll('[data-rm-teacher]').forEach(btn => btn.onclick = async () => {
      const teacher = catalog.teachers.find(t => t.id === btn.dataset.rmTeacher);
      if (!teacher) return;
      if (!confirm(`Удалить преподавателя «${teacher.fullName}»?`)) return;
      const dupes = catalog.rawTeachers.filter(t =>
        t.fullName.trim().toLowerCase() === teacher.fullName.trim().toLowerCase()
      );
      let allOk = true;
      for (const d of dupes) {
        if (!(await Db.removeCatalogTeacher(d.id))) allOk = false;
      }
      if (allOk) {
        window._catalogSelectedTeacher = '';
        showToast('Удалено');
        refresh();
      } else showToast('Ошибка удаления');
    });
  },

  async pageAdminClubLeaders() {
    if (!isTeacher(currentUser)) { showToast('Нет доступа'); return this.go('main'); }
    const gid = effectiveGroupId(currentUser);
    const gn = groupName(currentUser);
    const leaders = await Db.getClubLeaders(gid, gn);
    const types = [
      { key: 'CLUB', label: 'Кружки', list: leaders.filter(l => l.type === 'CLUB') },
      { key: 'SECTION', label: 'Секции', list: leaders.filter(l => l.type === 'SECTION') },
      { key: 'ELECTIVE', label: 'Факультативы', list: leaders.filter(l => l.type === 'ELECTIVE') }
    ];
    document.getElementById('app').innerHTML = UI.pageHeader('Руководители кружков', gn, '👥', 'purple menu-icon', '') +
      `<div class="page-content">${types.map(t => `
        <div class="menu-title">${t.label} (${t.list.length})</div>
        <button class="btn-sm btn-primary" data-add-leader="${t.key}" style="margin-bottom:8px">+ Добавить</button>
        ${t.list.map(l => `<div class="list-card">${escapeHtml(l.teacherName)}
          <button class="btn-sm btn-danger" data-rm-leader="${l.id}">×</button></div>`).join('') || '<p class="muted">Нет записей</p>'}`).join('')}</div>`;
    this.bindNav();
    document.querySelectorAll('[data-add-leader]').forEach(btn => btn.onclick = async () => {
      const act = await showModal('Новый руководитель', '<label class="form-label">ФИО</label><input class="form-input" id="dName">',
        [{ id: 'save', label: 'Добавить' }, { id: 'cancel', label: 'Отмена', class: 'btn-outline' }]);
      const name = document.getElementById('dName')?.value.trim();
      if (act !== 'save' || !name) return;
      await Db.addClubLeader({
        type: btn.dataset.addLeader,
        teacherId: currentUser.id,
        teacherName: name,
        groupId: gid,
        groupName: gn
      });
      showToast('Добавлено');
      this.pageAdminClubLeaders();
    });
    document.querySelectorAll('[data-rm-leader]').forEach(btn => btn.onclick = async () => {
      await Db.removeClubLeader(btn.dataset.rmLeader);
      this.pageAdminClubLeaders();
    });
  }
};

document.addEventListener('DOMContentLoaded', () => App.init());

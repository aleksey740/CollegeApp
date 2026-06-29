/* Утилиты — как в Android-приложении */

const DAY_ORDER = ['Понедельник', 'Вторник', 'Среда', 'Четверг', 'Пятница', 'Суббота'];

const SCHEDULE_TYPES = {
  LECTURE: 'Лекция', PRACTICE: 'Практика', LAB: 'Лабораторная',
  CONSULTATION: 'Консультация', EXAM: 'Экзамен', CONTROL_WORK: 'Контрольная',
  LUNCH: 'Обед', CURATOR_HOUR: 'Кураторский час', INFO_HOUR: 'Информационный час',
  SEMINAR: 'Семинар', OTHER: 'Другое'
};

const SCHEDULE_TYPE_COLORS = {
  LECTURE: '#3B82F6', PRACTICE: '#10B981', LAB: '#8B5CF6', CONSULTATION: '#06B6D4',
  EXAM: '#EF4444', CONTROL_WORK: '#F59E0B', LUNCH: '#64748B', CURATOR_HOUR: '#06B6D4',
  INFO_HOUR: '#A855F7', SEMINAR: '#EC4899', OTHER: '#94A3B8'
};

const PAIR_NUMBERS = ['1-я пара', '2-я пара', '3-я пара', '4-я пара', '5-я пара'];
const LESSON_NUMBERS = ['1-й урок', '2-й урок', '3-й урок', '4-й урок', '5-й урок', '6-й урок',
  '7-й урок', '8-й урок', '9-й урок', '10-й урок', '11-й урок'];

function groupNameToDocumentId(groupName) {
  const s = (groupName || '').trim().toLowerCase().replace(/[^a-zA-Z0-9_-]/g, '_').replace(/^_|_$/g, '');
  return s || 'item';
}

function effectiveGroupId(user) {
  const name = (user.groupName || user.group || '').trim();
  return name ? groupNameToDocumentId(name) : (user.groupId || '').trim();
}

function groupName(user) {
  return (user.groupName || user.group || '').trim();
}

function roleBadgeLabel(user) {
  if (user.role === 'teacher' || user.role === 'admin') return '👨‍🏫 Куратор';
  if (user.role === 'headman') return '⭐ Староста';
  if (user.gender === 'female') return '🎓 Учащаяся';
  return '🎓 Учащийся';
}

function roleBadgeClass(user) {
  return (user.role === 'teacher' || user.role === 'headman' || user.role === 'admin') ? 'purple' : '';
}

function roleBadgeColor(user) {
  if (user.role === 'teacher' || user.role === 'headman' || user.role === 'admin') return '#8B5CF6';
  return '#10B981';
}

function profileAvatarColor(user) {
  if (user.role === 'teacher' || user.role === 'headman') return '#8B5CF6';
  if (user.role === 'admin') return '#F59E0B';
  return '#10B981';
}

function displayName(user) {
  const name = (user.fullName || '').trim();
  return name || (user.email || '').split('@')[0] || 'Пользователь';
}

/** Видимость блоков главного экрана — как MainFragment.showUserSpecificView */
function mainMenuVisibility(user) {
  const role = user.role || 'student';
  const isStaff = role === 'teacher' || role === 'admin';
  const isLearner = role === 'student' || role === 'headman';
  const gn = (user.groupName || user.group || '').trim();
  return {
    quickStats: isLearner,
    grades: isLearner,
    absences: isLearner,
    schedule: isLearner,
    events: isLearner,
    nutrition: isLearner,
    clubs: isLearner,
    teacher: isStaff,
    headman: role === 'headman',
    admin: isStaff,
    notifications: true,
    chat: true,
    profile: true,
    groupBadge: !isStaff && !!gn
  };
}

function isTeacher(u) { return u.role === 'teacher' || u.role === 'admin'; }
function isHeadman(u) { return u.role === 'headman'; }
function isStudent(u) { return u.role === 'student'; }
function canEditEvents(u) { return isTeacher(u) || isHeadman(u); }

function canModify(currentUser, createdBy, createdByRole) {
  const ownerRole = createdByRole || (createdBy && createdBy === currentUser.id ? currentUser.role : '');
  if (isTeacher(currentUser)) {
    if (ownerRole === 'headman') return true;
    if (ownerRole === 'teacher' || ownerRole === 'admin') {
      return currentUser.role === 'admin' || createdBy === currentUser.id || !createdBy;
    }
    return !createdBy || createdBy === currentUser.id;
  }
  if (isHeadman(currentUser)) {
    if (ownerRole === 'teacher' || ownerRole === 'admin') return false;
    return !createdBy || createdBy === currentUser.id;
  }
  return false;
}

function getGreeting() {
  const h = new Date().getHours();
  if (h >= 5 && h <= 11) return 'Доброе утро! ☀️';
  if (h >= 12 && h <= 17) return 'Добрый день! 👋';
  if (h >= 18 && h <= 22) return 'Добрый вечер! 🌙';
  return 'Доброй ночи! 🌟';
}

function formatDateRu(date) {
  return date.toLocaleDateString('ru-RU', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
}

function formatTime(date) {
  if (!date || !(date instanceof Date) || isNaN(date)) return '';
  return date.toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' });
}

function formatDateShort(d) {
  const dt = typeof d === 'string' ? parseDate(d) : d;
  if (!dt) return d || '';
  return dt.toLocaleDateString('ru-RU');
}

function parseDate(value) {
  if (!value) return null;
  if (value instanceof Date) return value;
  if (value.toDate) return value.toDate();
  const p1 = value.match(/^(\d{2})\.(\d{2})\.(\d{4})$/);
  if (p1) return new Date(+p1[3], +p1[2] - 1, +p1[1]);
  const p2 = value.match(/^(\d{4})-(\d{2})-(\d{2})/);
  if (p2) return new Date(+p2[1], +p2[2] - 1, +p2[3]);
  const d = new Date(value);
  return isNaN(d) ? null : d;
}

function toDateString(date) {
  const d = date instanceof Date ? date : new Date();
  const dd = String(d.getDate()).padStart(2, '0');
  const mm = String(d.getMonth() + 1).padStart(2, '0');
  return `${dd}.${mm}.${d.getFullYear()}`;
}

function isWeekend(dateStr) {
  const d = parseDate(dateStr);
  if (!d) return false;
  const day = d.getDay();
  return day === 0 || day === 6;
}

function nextWeekday(date, delta) {
  const d = new Date(date);
  d.setDate(d.getDate() + delta);
  while (d.getDay() === 0 || d.getDay() === 6) d.setDate(d.getDate() + (delta > 0 ? 1 : -1));
  return d;
}

function isVisibleGrade(g) {
  const type = (g.type || '').toLowerCase();
  if (type.includes('неяв') || type === 'н') return false;
  return g.value >= 1 && g.value <= 10;
}

function gradeColorClass(value) {
  if (value >= 9) return 'grade-9';
  if (value >= 7) return 'grade-7';
  if (value >= 5) return 'grade-5m';
  return 'grade-2';
}

function avgGradeColor(avg) {
  if (avg >= 9) return '#10B981';
  if (avg >= 7) return '#A78BFA';
  if (avg >= 5) return '#F59E0B';
  if (avg > 0) return '#EF4444';
  return '#94A3B8';
}

function showToast(msg) {
  const el = document.getElementById('toast');
  if (!el) return;
  el.textContent = msg;
  el.classList.remove('hidden');
  clearTimeout(showToast._t);
  showToast._t = setTimeout(() => el.classList.add('hidden'), 3500);
}

function showModal(title, bodyHtml, buttons, onMount) {
  const overlay = document.createElement('div');
  overlay.className = 'modal-overlay';
  overlay.innerHTML = `
    <div class="modal-card">
      <h3 class="modal-title">${title}</h3>
      <div class="modal-body">${bodyHtml}</div>
      <div class="modal-actions">${buttons.map(b =>
        `<button class="${b.class || 'btn-primary'}" data-action="${b.id}">${b.label}</button>`
      ).join('')}</div>
    </div>`;
  document.body.appendChild(overlay);
  if (onMount) onMount(overlay);
  return new Promise(resolve => {
    overlay.querySelectorAll('[data-action]').forEach(btn => {
      btn.addEventListener('click', () => {
        overlay.remove();
        resolve(btn.dataset.action);
      });
    });
    overlay.addEventListener('click', e => {
      if (e.target === overlay) { overlay.remove(); resolve('cancel'); }
    });
  });
}

function loadingHtml() {
  return '<div class="loading-screen"><div class="spinner visible"></div><p class="loading-text">Загрузка…</p></div>';
}

function withTimeout(promise, ms, fallback) {
  return Promise.race([
    promise,
    new Promise(resolve => setTimeout(() => resolve(fallback), ms))
  ]);
}

function escapeHtml(s) {
  return String(s || '').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}

function initials(name) {
  const p = (name || '?').trim().split(/\s+/);
  if (p.length >= 2) return (p[0][0] + p[1][0]).toUpperCase();
  return (p[0][0] || '?').toUpperCase();
}

async function resolveCurrentSemester(user) {
  const gid = effectiveGroupId(user);
  if (!gid) return null;
  const semesters = await Db.getSemesters(gid);
  const today = new Date(); today.setHours(12,0,0,0);
  return semesters.find(s => {
    const start = parseDate(s.startDate);
    const end = parseDate(s.endDate);
    return start && end && today >= start && today <= end;
  }) || null;
}

function semesterNumber(name) {
  const m = (name || '').match(/(\d+)/);
  return m ? parseInt(m[1], 10) : null;
}

function filterBySemester(items, sem, dateField = 'date') {
  if (!sem) return items;
  const start = parseDate(sem.startDate);
  const end = parseDate(sem.endDate);
  if (!start || !end) return items;
  const sn = semesterNumber(sem.name);
  return items.filter(it => {
    const d = parseDate(it[dateField]);
    if (d && d >= start && d <= end) return true;
    if (sn && it.semester === sn) return true;
    return false;
  });
}

const GRADE_TYPES = ['Экзамен', 'Зачет', 'Лабораторная', 'Тест', 'Курсовая', 'Практическая работа', 'Обычная'];
const GRADE_VALUES = [10, 9, 8, 7, 6, 5, 4, 3, 2, 1];

function mondayOf(date) {
  const d = new Date(date instanceof Date ? date : new Date());
  const day = d.getDay();
  d.setDate(d.getDate() + (day === 0 ? -6 : 1 - day));
  d.setHours(12, 0, 0, 0);
  return d;
}

function weekDates(monday) {
  const out = [];
  for (let i = 0; i < 5; i++) {
    const d = new Date(monday);
    d.setDate(d.getDate() + i);
    out.push(toDateString(d));
  }
  return out;
}

function formatWeekLabel(monday) {
  const end = new Date(monday);
  end.setDate(end.getDate() + 4);
  return `${shortDate(monday)} — ${shortDate(end)}`;
}

function shortDate(d) {
  const dt = d instanceof Date ? d : parseDate(d);
  if (!dt) return '';
  return dt.toLocaleDateString('ru-RU', { day: '2-digit', month: '2-digit' });
}

function scheduleTypeLabel(type) {
  return SCHEDULE_TYPES[type] || type || '';
}

function scheduleTypeColor(type) {
  return SCHEDULE_TYPE_COLORS[type] || '#64748B';
}

function subjectDocumentId(name, groupId) {
  return normalizeAdminId(`${(name || '').trim()}_${(groupId || '').trim()}`);
}

function normalizeAdminId(input) {
  const s = (input || '').trim().toLowerCase()
    .replace(/[^a-z0-9а-яё_-]/gi, '_')
    .replace(/_+/g, '_')
    .replace(/^_|_$/g, '');
  return s || 'item';
}

function parseSubjectSemesters(data) {
  const d = data || {};
  let ids = Array.isArray(d.semesterIds) ? [...d.semesterIds] : [];
  let names = Array.isArray(d.semesterNames) ? [...d.semesterNames] : [];
  const legacyId = (d.semesterId || '').trim();
  const legacyName = (d.semesterName || '').trim();
  if (!ids.length && legacyId) {
    ids = [legacyId];
    names = [legacyName];
  }
  return { ids, names };
}

function subjectSemestersPayload(ids, names) {
  return {
    semesterIds: ids,
    semesterNames: names,
    semesterId: '',
    semesterName: ''
  };
}

function semestersDisplayText(subject, allSemesters) {
  let names = subject.semesterNames || [];
  if (!names.length && (subject.semesterIds || []).length && allSemesters?.length) {
    names = subject.semesterIds.map(id => allSemesters.find(s => s.id === id)?.name).filter(Boolean);
  }
  return names.length ? `Семестры: ${names.join(', ')}` : 'Семестры: не назначены';
}

function semesterLabel(sem) {
  if (sem?.startDate && sem?.endDate) return `${sem.name} (${sem.startDate} — ${sem.endDate})`;
  return sem?.name || '';
}

function semesterNumberFromName(name) {
  const m = (name || '').match(/(\d+)/);
  return m ? parseInt(m[1], 10) : null;
}

function mergeTeachersByName(source) {
  const groups = new Map();
  (source || []).forEach(t => {
    const key = (t.fullName || '').trim().toLowerCase();
    if (!key) return;
    const prev = groups.get(key);
    if (!prev) {
      groups.set(key, { id: t.id, fullName: t.fullName, subjectIds: [...(t.subjectIds || [])], rawIds: [t.id] });
    } else {
      prev.subjectIds = [...new Set([...prev.subjectIds, ...(t.subjectIds || [])])];
      prev.rawIds.push(t.id);
      if (t.id < prev.id) prev.id = t.id;
    }
  });
  return [...groups.values()].sort((a, b) => a.fullName.localeCompare(b.fullName, 'ru'));
}

function shortFio(fullName) {
  const p = (fullName || '').trim().split(/\s+/);
  if (p.length < 2) return fullName || '';
  return `${p[0]} ${p[1][0]}.${p.length > 2 ? p[2][0] + '.' : ''}`;
}

function formatTeachersLine(item) {
  if (item.type === 'LUNCH') return '';
  const first = shortFio(item.teacherName);
  if (item.isSubgroup && item.teacherName2) {
    return `👨‍🏫 ${first} / ${shortFio(item.teacherName2)}`;
  }
  return item.teacherName ? `👨‍🏫 ${first || item.teacherName}` : '';
}

function formatRoomLine(item) {
  if (item.type === 'LUNCH') return '';
  if (item.isSubgroup && item.room2) return `🏫 ${item.room} / ${item.room2}`;
  return item.room ? `🏫 ${item.room}` : '';
}

function currentWeekKey() {
  const m = mondayOf(new Date());
  return `${m.getFullYear()}-${String(m.getMonth() + 1).padStart(2, '0')}-${String(m.getDate()).padStart(2, '0')}`;
}

function mondayOfWeek(dateStr) {
  const d = parseDate(dateStr) || new Date();
  return mondayOf(d);
}

function isDateInAnySemester(dateStr, semesters) {
  const d = parseDate(dateStr);
  if (!d || !semesters?.length) return true;
  return semesters.some(s => {
    const start = parseDate(s.startDate), end = parseDate(s.endDate);
    return start && end && d >= start && d <= end;
  });
}

function filterCuratorialByDate(items, single, from, to) {
  let out = items;
  if (single) out = out.filter(i => i.date === single);
  else if (from && to) {
    const f = parseDate(from), t = parseDate(to);
    if (f && t) out = out.filter(i => {
      const d = parseDate(i.date);
      return d && d >= f && d <= t;
    });
  }
  return out.sort((a, b) => (b.date || '').localeCompare(a.date || '') || (b.time || '').localeCompare(a.time || ''));
}

function formatChatDayLabel(date) {
  if (!date || !(date instanceof Date) || isNaN(date)) return '';
  const now = new Date();
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const msgDay = new Date(date.getFullYear(), date.getMonth(), date.getDate());
  const diff = Math.round((today - msgDay) / 86400000);
  if (diff === 0) return 'Сегодня';
  if (diff === 1) return 'Вчера';
  return date.toLocaleDateString('ru-RU', { weekday: 'long', day: 'numeric', month: 'long' });
}

function selectOptions(list, selected, emptyLabel) {
  const opts = emptyLabel ? [`<option value="">${escapeHtml(emptyLabel)}</option>`] : [];
  list.forEach(v => {
    opts.push(`<option ${selected === v ? 'selected' : ''}>${escapeHtml(v)}</option>`);
  });
  return opts.join('');
}

function displayGradeCell(g) {
  if (!g) return '';
  if (g.value === 0 || (g.type || '').toLowerCase().includes('неяв')) return 'Н';
  return String(g.value);
}

function hashParams() {
  const q = location.hash.split('?')[1] || '';
  return new URLSearchParams(q);
}

function userFromAuth(fu) {
  const email = fu.email || '';
  return {
    id: fu.uid, email,
    fullName: fu.displayName || email.split('@')[0] || 'Пользователь',
    role: 'student', gender: 'male',
    group: '', groupId: '', groupName: '',
    phone: '', address: '', birthDate: '',
    parentName: '', parentPhone: ''
  };
}

const ABSENCE_REASONS = [
  { v: 'WITHOUT_REASON', l: 'Без уважительной причины' },
  { v: 'SICK', l: 'Болезнь' },
  { v: 'FAMILY', l: 'Семейные обстоятельства' },
  { v: 'OFFICIAL', l: 'По служебной записке' },
  { v: 'OTHER', l: 'Другое' }
];

function absenceReasonLabel(code) {
  return ABSENCE_REASONS.find(r => r.v === code)?.l || code || '';
}

const JOURNAL_COLUMN_TYPES = ['', 'ОКР', 'ЛР', 'ПР'];
const GRADE_TYPE_OPTIONS = ['Обычная', 'Контрольная', 'Лабораторная', 'Практическая', 'Тест', 'Экзамен', 'Зачет'];

const RU_DAYS = ['Воскресенье', 'Понедельник', 'Вторник', 'Среда', 'Четверг', 'Пятница', 'Суббота'];

function dayNameFromDate(dateStr) {
  const d = parseDate(dateStr);
  return d ? RU_DAYS[d.getDay()] : '';
}

function filterScheduleByDate(items, dateStr) {
  return items.filter(i => i.date === dateStr).sort((a, b) => (a.time || '').localeCompare(b.time || ''));
}

function exportCsv(filename, headers, rows) {
  const esc = v => `"${String(v ?? '').replace(/"/g, '""')}"`;
  const csv = [headers.map(esc).join(';'), ...rows.map(r => r.map(esc).join(';'))].join('\n');
  const blob = new Blob(['\ufeff' + csv], { type: 'text/csv;charset=utf-8' });
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = filename;
  a.click();
  URL.revokeObjectURL(a.href);
}

/** Печать / «Сохранить как PDF» — аналог PDF-экспорта Android */
function exportPrint(title, headers, rows) {
  const head = headers.map(h => `<th>${escapeHtml(h)}</th>`).join('');
  const body = rows.map(r => `<tr>${r.map(c => `<td>${escapeHtml(c)}</td>`).join('')}</tr>`).join('');
  const html = `<!DOCTYPE html><html lang="ru"><head><meta charset="UTF-8"><title>${escapeHtml(title)}</title>
<style>body{font-family:Segoe UI,sans-serif;padding:24px;color:#111}h1{font-size:18px;margin-bottom:16px}
table{border-collapse:collapse;width:100%}th,td{border:1px solid #ccc;padding:8px;text-align:left;font-size:13px}
th{background:#f3f4f6}</style></head><body><h1>${escapeHtml(title)}</h1>
<table><thead><tr>${head}</tr></thead><tbody>${body}</tbody></table></body></html>`;
  const w = window.open('', '_blank');
  if (!w) { showToast('Разрешите всплывающие окна для экспорта'); return; }
  w.document.write(html);
  w.document.close();
  w.focus();
  setTimeout(() => w.print(), 300);
}

const ROUTE_ACCESS = {
  grades: u => isStudent(u) || isHeadman(u),
  absences: u => isStudent(u) || isHeadman(u),
  'manage-absences': u => isTeacher(u) || isHeadman(u),
  journal: u => isTeacher(u),
  students: u => isTeacher(u),
  'student-info': u => isTeacher(u),
  'student-grades': u => isTeacher(u),
  statistics: u => isTeacher(u) || isHeadman(u),
  curatorial: u => isTeacher(u),
  'admin-catalog': u => isTeacher(u),
  'admin-club-leaders': u => isTeacher(u),
  'edit-profile': u => isStudent(u) || isHeadman(u),
  teacher: u => isTeacher(u),
  headman: u => isHeadman(u),
  admin: u => isTeacher(u)
};

function canAccessRoute(route, user) {
  if (!ROUTE_ACCESS[route]) return true;
  return ROUTE_ACCESS[route](user);
}

function hubTitle(user, fallback) {
  return (user.fullName || '').trim() || fallback;
}

function getPrivacyPolicyHtml() {
  const p = t => `<p>${escapeHtml(t)}</p>`;
  return `<h2 class="legal-heading">ПОЛИТИКА КОНФИДЕНЦИАЛЬНОСТИ МОБИЛЬНОГО ПРИЛОЖЕНИЯ «ПОМОЩНИК КУРАТОРА»</h2>
    ${p('Дата вступления в силу: 24.03.2026')}
    <h3>1. Общие положения</h3>
    ${p('Настоящая Политика конфиденциальности определяет порядок сбора, использования, хранения и защиты данных пользователей мобильного приложения «Помощник куратора» (далее — «Приложение»).')}
    ${p('Использование Приложения означает согласие пользователя с условиями настоящей Политики.')}
    <h3>2. Оператор и используемая инфраструктура</h3>
    ${p('Приложение использует сервисы Firebase (Google), включая механизмы аутентификации, хранения и обработки данных, а также push-уведомления.')}
    <h3>3. Категории обрабатываемых данных</h3>
    ${p('3.1. Данные учётной записи: адрес электронной почты; технический идентификатор пользователя.')}
    ${p('3.2. Данные профиля: ФИО; пол; роль; учебная группа; иные персональные данные, добровольно добавленные пользователем.')}
    ${p('3.3. Образовательные данные: отметки; пропуски; расписание; сообщения чата; уведомления.')}
    ${p('3.4. Технические данные: служебные данные для работы приложения; push-токен устройства.')}
    ${p('Примечание: пароль не хранится в открытом виде и обрабатывается Firebase Authentication.')}
    <h3>4–8. Цели, основания, передача, защита, хранение</h3>
    ${p('Данные обрабатываются для регистрации, предоставления функций по роли, отображения образовательной информации и отправки уведомлений. Данные не продаются третьим лицам. Передача возможна поставщикам инфраструктуры (Firebase/Google) и по закону.')}
    <h3>9. Права пользователя</h3>
    ${p('Пользователь вправе запросить информацию, исправление или удаление данных. Запросы: через администратора организации или email assistantcurator2026@gmail.com.')}
    <h3>10–11. Изменения и контакты</h3>
    ${p('Актуальная редакция публикуется в Приложении. Контакт: assistantcurator2026@gmail.com.')}`;
}

function semesterFromDate(user, dateStr) {
  return null; // resolved async via Db.getSemesters
}

function studentRoleLabel(gender) {
  return gender === 'female' ? 'Учащаяся' : 'Учащийся';
}

function refreshRegisterRoleOptions(genderEl, roleEl, preserveSelection = true) {
  const gender = genderEl.value;
  const current = roleEl.value;
  const studentVal = 'student';
  const options = [
    { v: studentVal, l: studentRoleLabel(gender) },
    { v: 'headman', l: 'Староста' },
    { v: 'teacher', l: 'Куратор' }
  ];
  roleEl.innerHTML = options.map(o => `<option value="${o.v}">${o.l}</option>`).join('');
  if (preserveSelection && (current === 'headman' || current === 'teacher')) {
    roleEl.value = current;
  } else {
    roleEl.value = studentVal;
  }
}

function clearAuthFieldErrors() {
  document.querySelectorAll('.field-error').forEach(el => { el.textContent = ''; });
  document.querySelectorAll('.input-error').forEach(el => el.classList.remove('input-error'));
}

function setAuthFieldError(fieldId, errorId, message) {
  const field = document.getElementById(fieldId);
  const err = document.getElementById(errorId);
  if (err) err.textContent = message || '';
  if (field) field.classList.toggle('input-error', !!message);
}

function setupPasswordToggles(root = document) {
  root.querySelectorAll('.password-wrap').forEach(wrap => {
    const input = wrap.querySelector('input');
    const btn = wrap.querySelector('.password-toggle');
    if (!input || !btn) return;
    btn.onclick = () => {
      const show = input.type === 'password';
      input.type = show ? 'text' : 'password';
      btn.textContent = show ? '🙈' : '👁';
      btn.setAttribute('aria-label', show ? 'Скрыть пароль' : 'Показать пароль');
    };
  });
}

function mapRegisterFirebaseError(e) {
  const msg = e?.message || '';
  if (msg.includes('email address is already') || e?.code === 'auth/email-already-in-use') return 'Этот email уже зарегистрирован';
  if (msg.includes('password') || e?.code === 'auth/weak-password') return 'Пароль слишком слабый';
  if (msg.includes('network') || e?.code === 'auth/network-request-failed') return 'Проверьте подключение к интернету';
  return msg || 'Неизвестная ошибка';
}

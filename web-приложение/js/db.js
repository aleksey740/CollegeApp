/* Firestore — все коллекции Android-приложения */

const Db = (() => {
  let auth = null;
  let db = null;

  function init() {
    if (typeof firebase === 'undefined') throw new Error('Firebase SDK не загружен');
    if (!firebase.apps.length) firebase.initializeApp(FIREBASE_CONFIG);
    auth = firebase.auth();
    db = firebase.firestore();
  }

  function ts() { return firebase.firestore.FieldValue.serverTimestamp(); }

  function mapUser(doc) {
    const d = doc.data() || {};
    return {
      id: doc.id, email: d.email || '', fullName: d.fullName || '',
      role: d.role || 'student', gender: d.gender || 'male',
      group: d.group || '', groupId: d.groupId || '', groupName: d.groupName || '',
      phone: d.phone || '', address: d.address || '', birthDate: d.birthDate || '',
      parentName: d.parentName || '', parentPhone: d.parentPhone || '',
      parentName2: d.parentName2 || '', parentPhone2: d.parentPhone2 || '',
      mealAutoPlanEnabled: !!d.mealAutoPlanEnabled,
      mealAutoPlanLastAppliedWeek: d.mealAutoPlanLastAppliedWeek || '',
      livesInDormitory: !!d.livesInDormitory, isDisabled: !!d.isDisabled,
      isLargeFamily: !!d.isLargeFamily, fundingType: d.fundingType || '',
      isLowIncome: !!d.isLowIncome, isOrphan: !!d.isOrphan, isNonResident: !!d.isNonResident
    };
  }

  async function fetchUser(uid) {
    try {
      const doc = await db.collection('users').doc(uid).get();
      if (!doc.exists) return null;
      return mapUser(doc);
    } catch (e) {
      console.error(e);
      showToast(e.code === 'permission-denied' ? 'Нет доступа к Firestore' : 'Ошибка загрузки профиля');
      return null;
    }
  }

  async function getGroupStudents(groupName) {
    const gid = groupNameToDocumentId(groupName);
    const roles = ['student', 'headman'];
    const results = [];
    for (const role of roles) {
      for (const field of ['groupName', 'groupId']) {
        const val = field === 'groupId' ? gid : groupName;
        const snap = await db.collection('users').where('role', '==', role).where(field, '==', val).get();
        snap.docs.forEach(d => results.push(mapUser(d)));
      }
    }
    const map = new Map();
    results.forEach(u => map.set(u.id, u));
    return [...map.values()].sort((a, b) => a.fullName.localeCompare(b.fullName, 'ru'));
  }

  async function countRoleInGroup(groupId, role) {
    const snap = await db.collection('users').where('groupId', '==', groupId).where('role', '==', role).get();
    return snap.size;
  }

  async function canRegisterToGroup(role, groupId) {
    if (!groupId) return true;
    const limDoc = await db.collection('group_limits').doc(groupId).get();
    if (!limDoc.exists) return true;
    const l = limDoc.data();
    const teachers = await countRoleInGroup(groupId, 'teacher');
    const students = await countRoleInGroup(groupId, 'student');
    const headmen = await countRoleInGroup(groupId, 'headman');
    const ok = (limit, cur) => limit == null || cur < limit;
    if (role === 'teacher') return ok(l.teacherLimit, teachers);
    if (role === 'headman') return ok(l.headmanLimit, headmen) && ok(l.studentLimit, students + headmen);
    if (role === 'student') return ok(l.studentLimit, students + headmen);
    return true;
  }

  async function registerUser(cred, data) {
    const group = data.group.trim();
    const groupId = groupNameToDocumentId(group);
    const allowed = await canRegisterToGroup(data.role, groupId);
    if (!allowed) { await cred.user.delete(); throw new Error('Лимит для группы превышен'); }
    await db.collection('users').doc(cred.user.uid).set({
      email: data.email, fullName: data.fullName, role: data.role, gender: data.gender,
      group, groupId, groupName: group,
      address: '', birthDate: '', phone: '', parentName: '', parentPhone: '',
      parentName2: '', parentPhone2: '', mealAutoPlanEnabled: false, mealAutoPlanLastAppliedWeek: '',
      livesInDormitory: false, isDisabled: false, isLargeFamily: false, fundingType: '',
      isLowIncome: false, isOrphan: false, isNonResident: false
    });
  }

  async function updatePersonalInfo(uid, fields) {
    await db.collection('users').doc(uid).update(fields);
  }

  async function deletePersonalData(uid) {
    await db.collection('users').doc(uid).update({
      address: '', birthDate: '', phone: '', parentName: '', parentPhone: '',
      parentName2: '', parentPhone2: '', fundingType: '',
      livesInDormitory: false, isDisabled: false, isLargeFamily: false,
      isLowIncome: false, isOrphan: false, isNonResident: false
    });
  }

  // --- Grades ---
  async function getStudentGrades(studentId) {
    const snap = await db.collection('grades').where('studentId', '==', studentId).get();
    return snap.docs.map(d => mapGrade(d.id, d.data())).sort((a, b) => (b.date || '').localeCompare(a.date || ''));
  }

  async function getTeacherGrades(teacherId) {
    const snap = await db.collection('grades').where('teacherId', '==', teacherId).get();
    return snap.docs.map(d => mapGrade(d.id, d.data()));
  }

  function mapGrade(id, d) {
    d = d || {};
    return {
      id, studentId: d.studentId || '', studentName: d.studentName || '',
      subject: d.subject || '', value: d.value || 0, date: d.date || '',
      type: d.type || '', teacherId: d.teacherId || '', teacherName: d.teacherName || '',
      comment: d.comment || '', semester: d.semester || 1,
      createdAt: d.createdAt || 0
    };
  }

  async function saveGrade(grade, isNew) {
    const data = {
      studentId: grade.studentId, studentName: grade.studentName,
      subject: grade.subject, value: grade.value, date: grade.date,
      type: grade.type || '', teacherId: grade.teacherId, teacherName: grade.teacherName,
      comment: grade.comment || '', semester: grade.semester || 1,
      createdAt: grade.createdAt || Date.now()
    };
    if (isNew || !grade.id) {
      const ref = await db.collection('grades').add(data);
      if (grade.notifyUserId && grade.value >= 1 && grade.value <= 10) {
        await createNotification({
          userId: grade.notifyUserId,
          title: 'Новая отметка',
          message: `${grade.subject}: ${grade.value}`,
          type: 'GRADE'
        });
      }
      return ref.id;
    }
    await db.collection('grades').doc(grade.id).set(data);
    return grade.id;
  }

  async function deleteGrade(id) {
    await db.collection('grades').doc(id).delete();
  }

  // --- Absences ---
  function mapAbsence(id, d) {
    d = d || {};
    return {
      id, studentId: d.studentId || '', studentName: d.studentName || '',
      subject: d.subject || '', date: d.date || '', hours: d.hours || 0,
      reason: d.reason || '', excused: !!(d.isExcused ?? d.excused),
      comment: d.comment || '', studentGroup: d.studentGroup || '',
      createdBy: d.createdBy || '', createdByName: d.createdByName || '',
      createdByRole: d.createdByRole || ''
    };
  }

  async function getStudentAbsences(studentId) {
    const snap = await db.collection('absences').where('studentId', '==', studentId).get();
    return snap.docs.map(d => mapAbsence(d.id, d.data())).sort((a, b) => (b.date || '').localeCompare(a.date || ''));
  }

  async function getGroupAbsences(groupName) {
    const snap = await db.collection('absences').where('studentGroup', '==', groupName).get();
    return snap.docs.map(d => mapAbsence(d.id, d.data())).sort((a, b) => (b.date || '').localeCompare(a.date || ''));
  }

  async function saveAbsence(absence, isNew) {
    const data = {
      studentId: absence.studentId, studentName: absence.studentName,
      subject: absence.subject, date: absence.date, hours: absence.hours,
      reason: absence.reason || 'WITHOUT_REASON', isExcused: !!absence.excused,
      comment: absence.comment || '', studentGroup: absence.studentGroup,
      createdBy: absence.createdBy, createdByName: absence.createdByName || '',
      createdByRole: absence.createdByRole, createdAt: absence.createdAt || Date.now()
    };
    if (isNew) {
      const ref = await db.collection('absences').add(data);
      await createNotification({
        userId: absence.studentId,
        title: 'Пропуск зафиксирован',
        message: `${absence.subject}, ${absence.date}: ${absence.hours} ч.`,
        type: 'ABSENCE'
      });
      return ref.id;
    }
    await db.collection('absences').doc(absence.id).set(data);
    return absence.id;
  }

  async function deleteAbsence(id) {
    await db.collection('absences').doc(id).delete();
  }

  // --- Schedule ---
  function mapSchedule(id, d) {
    d = d || {};
    return {
      id, group: d.group || '', day: d.day || '', date: d.date || '',
      time: d.time || '', subject: d.subject || '', room: d.room || '',
      description: d.description || '', type: d.type || '',
      teacherName: d.teacherName || '', teacherName2: d.teacherName2 || '',
      room2: d.room2 || '', isSubgroup: !!d.isSubgroup,
      assignedStudentIds: d.assignedStudentIds || [],
      createdBy: d.createdBy || '', createdByRole: d.createdByRole || ''
    };
  }

  function subscribeSchedule(group, cb) {
    return db.collection('schedule').where('group', '==', group)
      .onSnapshot(snap => {
        const items = snap.docs.map(d => mapSchedule(d.id, d.data())).sort((a, b) => {
          const da = DAY_ORDER.indexOf(a.day), db2 = DAY_ORDER.indexOf(b.day);
          if (da !== db2) return da - db2;
          return (a.time || '').localeCompare(b.time || '');
        });
        cb(items);
      }, err => { console.error(err); cb([]); });
  }

  async function getScheduleForGroup(group) {
    const snap = await db.collection('schedule').where('group', '==', group).get();
    return snap.docs.map(d => mapSchedule(d.id, d.data())).sort((a, b) => {
      const da = DAY_ORDER.indexOf(a.day), db2 = DAY_ORDER.indexOf(b.day);
      if (da !== db2) return da - db2;
      return (a.time || '').localeCompare(b.time || '');
    });
  }

  async function saveSchedule(item, isNew) {
    const data = { ...item };
    delete data.id;
    if (isNew) { const ref = await db.collection('schedule').add(data); return ref.id; }
    await db.collection('schedule').doc(item.id).set(data);
    return item.id;
  }

  async function deleteSchedule(id) {
    await db.collection('schedule').doc(id).delete();
  }

  // --- Events ---
  function mapEvent(id, d) {
    d = d || {};
    return {
      id, title: d.title || '', date: d.date || '', time: d.time || '',
      place: d.place || '', description: d.description || '',
      groupName: d.groupName || '', createdBy: d.createdBy || '',
      createdByName: d.createdByName || '', createdByRole: d.createdByRole || ''
    };
  }

  async function getGroupEvents(groupName) {
    const snap = await db.collection('group_events').where('groupName', '==', groupName).get();
    return snap.docs.map(d => mapEvent(d.id, d.data())).sort((a, b) =>
      (a.date + a.time).localeCompare(b.date + b.time));
  }

  async function saveEvent(ev, isNew) {
    const data = {
      title: ev.title, date: ev.date, time: ev.time, place: ev.place || '',
      description: ev.description || '', groupName: ev.groupName,
      createdBy: ev.createdBy, createdByName: ev.createdByName, createdByRole: ev.createdByRole,
      createdAt: ev.createdAt || Date.now()
    };
    if (isNew) {
      const ref = await db.collection('group_events').add(data);
      await notifyGroup(ev.groupName, ev.createdBy, 'Новое мероприятие', ev.title, 'EVENT');
      return ref.id;
    }
    await db.collection('group_events').doc(ev.id).update({
      title: ev.title, date: ev.date, time: ev.time, place: ev.place, description: ev.description
    });
    return ev.id;
  }

  async function deleteEvent(id) {
    await db.collection('group_events').doc(id).delete();
  }

  // --- Meal ---
  async function getMealSubscription(date, userId) {
    const doc = await db.collection('meal_subscriptions').doc(`${date}_${userId}`).get();
    if (!doc.exists) return null;
    const d = doc.data();
    return { id: doc.id, date: d.date, userId: d.userId, userName: d.userName, isSubscribed: !!d.isSubscribed };
  }

  async function setMealSubscription(date, user, subscribed) {
    if (subscribed && isWeekend(date)) return false;
    const docId = `${date}_${user.id}`;
    await db.collection('meal_subscriptions').doc(docId).set({
      date, userId: user.id, userName: user.fullName || user.email,
      groupName: user.groupName, isSubscribed: subscribed,
      updatedById: user.id, updatedAt: ts()
    });
    return true;
  }

  async function getMealSubscribers(date, groupName) {
    let q = db.collection('meal_subscriptions').where('date', '==', date).where('isSubscribed', '==', true);
    if (groupName) q = q.where('groupName', '==', groupName);
    const snap = await q.get();
    return snap.docs.map(d => {
      const x = d.data();
      return { userName: x.userName, userId: x.userId };
    }).sort((a, b) => a.userName.localeCompare(b.userName, 'ru'));
  }

  async function setMealAutoPlan(uid, enabled, lastAppliedWeek) {
    const data = { mealAutoPlanEnabled: enabled };
    if (lastAppliedWeek !== undefined) data.mealAutoPlanLastAppliedWeek = lastAppliedWeek;
    await db.collection('users').doc(uid).update(data);
  }

  async function applyWeeklyMealPlan(user, subscribe, weekMonday) {
    const mon = weekMonday || mondayOf(new Date());
    let ok = 0;
    for (let i = 0; i < 5; i++) {
      const d = new Date(mon);
      d.setDate(d.getDate() + i);
      const dateStr = toDateString(d);
      const res = await setMealSubscription(dateStr, user, subscribe);
      if (res) ok++;
    }
    return ok;
  }

  async function ensureWeeklyMealAutoPlan(user) {
    if (!user.mealAutoPlanEnabled) return user;
    const weekKey = currentWeekKey();
    if (user.mealAutoPlanLastAppliedWeek === weekKey) return user;
    await applyWeeklyMealPlan(user, true, mondayOf(new Date()));
    await setMealAutoPlan(user.id, true, weekKey);
    return fetchUser(user.id);
  }

  async function getGradesForStudents(studentIds) {
    const pairs = await Promise.all(studentIds.map(async id => [id, await getStudentGrades(id)]));
    const map = {};
    pairs.forEach(([id, grades]) => { map[id] = grades; });
    return map;
  }

  function subscribeNotifications(userId, cb) {
    return db.collection('notifications').where('userId', '==', userId)
      .onSnapshot(snap => {
        const list = snap.docs.map(d => {
          const x = d.data();
          let createdAt = x.createdAt;
          if (createdAt && createdAt.toDate) createdAt = createdAt.toDate();
          else createdAt = new Date();
          return {
            id: d.id, title: x.title || '', message: x.message || '',
            type: x.type || 'SYSTEM', isRead: !!x.isRead, createdAt
          };
        }).sort((a, b) => b.createdAt - a.createdAt);
        cb(list);
      }, err => { console.error(err); cb([]); });
  }

  async function updateCatalogTeacher(id, fullName, subjectIds) {
    try {
      const name = (fullName || '').trim();
      if (!name || !id) return false;
      await db.collection('admin_teachers').doc(id).set({
        fullName: name,
        subjectIds: [...new Set((subjectIds || []).filter(Boolean))]
      }, { merge: true });
      return true;
    } catch (e) {
      console.error(e);
      return false;
    }
  }

  async function updateTeachersByName(rawTeachers, teacherName, mutateIds) {
    const key = (teacherName || '').trim().toLowerCase();
    const dupes = (rawTeachers || []).filter(t => t.fullName.trim().toLowerCase() === key);
    let ok = true;
    for (const d of dupes) {
      const next = mutateIds([...(d.subjectIds || [])]);
      if (!(await updateCatalogTeacher(d.id, d.fullName, next))) ok = false;
    }
    return ok;
  }

  async function removeCatalogTeacher(id) {
    if (!id) return false;
    await db.collection('admin_teachers').doc(id).delete();
    return true;
  }

  // --- Clubs ---
  function mapClub(id, d) {
    d = d || {};
    return {
      id, name: d.name || '', description: d.description || '', type: d.type || 'club',
      schedule: d.schedule || '', location: d.location || '', groupId: d.groupId || '',
      teacherId: d.teacherId || '', teacherName: d.teacherName || '', maxParticipants: d.maxParticipants || 0,
      participantIds: d.participantIds || [], isActive: d.isActive !== false,
      nextSession: d.nextSession || ''
    };
  }

  async function getClubsForGroup(groupId, teacherId) {
    let snap;
    if (teacherId) {
      snap = await db.collection('clubs').where('teacherId', '==', teacherId).get();
    } else {
      snap = await db.collection('clubs').where('groupId', '==', groupId).get();
    }
    return snap.docs.map(d => mapClub(d.id, d.data())).filter(c => c.isActive);
  }

  async function saveClub(club, isNew) {
    const data = { ...club };
    delete data.id;
    if (isNew) { const ref = await db.collection('clubs').add(data); return ref.id; }
    await db.collection('clubs').doc(club.id).set(data, { merge: true });
    return club.id;
  }

  async function toggleClubParticipant(clubId, userId, join) {
    const ref = db.collection('clubs').doc(clubId);
    const doc = await ref.get();
    if (!doc.exists) return false;
    let ids = [...(doc.data().participantIds || [])];
    if (join && !ids.includes(userId)) ids.push(userId);
    if (!join) ids = ids.filter(id => id !== userId);
    await ref.update({ participantIds: ids });
    return true;
  }

  // --- Notifications ---
  async function getNotifications(userId) {
    const snap = await db.collection('notifications').where('userId', '==', userId).get();
    return snap.docs.map(d => {
      const x = d.data();
      let createdAt = x.createdAt;
      if (createdAt && createdAt.toDate) createdAt = createdAt.toDate();
      else createdAt = new Date();
      return {
        id: d.id, title: x.title || '', message: x.message || '',
        type: x.type || 'SYSTEM', isRead: !!x.isRead, createdAt
      };
    }).sort((a, b) => b.createdAt - a.createdAt);
  }

  async function getUnreadCount(userId) {
    try {
      const snap = await db.collection('notifications')
        .where('userId', '==', userId).where('isRead', '==', false).get();
      return snap.size;
    } catch (_) {
      const list = await getNotifications(userId);
      return list.filter(n => !n.isRead).length;
    }
  }

  async function createNotification(n) {
    await db.collection('notifications').add({
      userId: n.userId, title: n.title, message: n.message,
      type: n.type || 'SYSTEM', isRead: false, createdAt: ts(),
      relatedId: n.relatedId || '', relatedType: n.relatedType || ''
    });
  }

  async function markNotificationRead(id) {
    await db.collection('notifications').doc(id).update({ isRead: true });
  }

  async function markAllNotificationsRead(userId) {
    const snap = await db.collection('notifications').where('userId', '==', userId).where('isRead', '==', false).get();
    const batch = db.batch();
    snap.docs.forEach(d => batch.update(d.ref, { isRead: true }));
    await batch.commit();
  }

  async function deleteNotification(id) {
    await db.collection('notifications').doc(id).delete();
  }

  async function deleteAllNotifications(userId) {
    const snap = await db.collection('notifications').where('userId', '==', userId).get();
    const batch = db.batch();
    snap.docs.forEach(d => batch.delete(d.ref));
    await batch.commit();
  }

  async function notifyGroup(groupName, excludeUserId, title, message, type) {
    const users = await getGroupStudents(groupName);
    for (const u of users) {
      if (u.id === excludeUserId) continue;
      await createNotification({ userId: u.id, title, message, type });
    }
    const teachers = await db.collection('users').where('role', '==', 'teacher')
      .where('groupName', '==', groupName).get();
    for (const d of teachers.docs) {
      if (d.id === excludeUserId) continue;
      await createNotification({ userId: d.id, title, message, type });
    }
  }

  // --- Chat ---
  function buildGroupRoomId(user) {
    const gid = effectiveGroupId(user);
    if (!gid) return null;
    return `group_${gid}`;
  }

  async function ensureChatRoom(user) {
    const roomId = buildGroupRoomId(user);
    if (!roomId) return null;
    const gn = groupName(user) || 'Группа';
    const ref = db.collection('chat_rooms').doc(roomId);
    const doc = await ref.get();
    if (!doc.exists) {
      await ref.set({
        title: `Чат группы ${gn}`, description: 'Сообщения внутри вашей группы',
        isGroupRoom: true, groupId: user.groupId || gn, groupName: gn,
        lastMessage: '', lastSenderName: ''
      });
    }
    return { id: roomId, title: `Чат группы ${gn}`, ...(doc.exists ? doc.data() : {}) };
  }

  function subscribeMessages(roomId, cb) {
    return db.collection('chat_messages').where('roomId', '==', roomId)
      .onSnapshot(snap => {
        const msgs = snap.docs.map(d => {
          const x = d.data();
          let createdAt = x.createdAt;
          if (createdAt && createdAt.toDate) createdAt = createdAt.toDate();
          else createdAt = new Date();
          return {
            id: d.id, roomId: x.roomId, senderId: x.senderId,
            senderName: x.senderName || '', senderRole: x.senderRole || '',
            text: x.text || '', stickerId: x.stickerId || '', createdAt
          };
        }).sort((a, b) => a.createdAt - b.createdAt);
        cb(msgs);
      });
  }

  function subscribeRoom(roomId, cb) {
    return db.collection('chat_rooms').doc(roomId).onSnapshot(doc => {
      if (doc.exists) cb({ id: doc.id, ...doc.data() });
    });
  }

  async function sendChatMessage(roomId, user, text) {
    const trimmed = text.trim();
    if (!trimmed) return false;
    const name = user.fullName || user.email.split('@')[0];
    await db.collection('chat_messages').add({
      roomId, senderId: user.id, senderName: name, senderRole: user.role,
      text: trimmed, links: [], createdAt: ts()
    });
    await db.collection('chat_rooms').doc(roomId).set({
      lastMessage: trimmed.slice(0, 250), lastSenderName: name, lastMessageAt: ts()
    }, { merge: true });
    await notifyGroup(groupName(user), user.id, 'Новое сообщение в чате', trimmed.slice(0, 100), 'CHAT');
    return true;
  }

  async function sendChatSticker(roomId, user, stickerId) {
    const name = user.fullName || user.email.split('@')[0];
    await db.collection('chat_messages').add({
      roomId, senderId: user.id, senderName: name, senderRole: user.role,
      text: '', stickerId, createdAt: ts()
    });
    await db.collection('chat_rooms').doc(roomId).set({
      lastMessage: 'Стикер', lastSenderName: name, lastMessageAt: ts()
    }, { merge: true });
    return true;
  }

  async function deleteChatMessage(id) {
    await db.collection('chat_messages').doc(id).delete();
  }

  async function pinChatMessage(roomId, msg, userName) {
    const preview = msg.stickerId ? 'Стикер' : (msg.text || '').slice(0, 250);
    await db.collection('chat_rooms').doc(roomId).set({
      pinnedMessageId: msg.id, pinnedMessageText: preview,
      pinnedByName: userName, pinnedAt: ts()
    }, { merge: true });
  }

  async function clearChatMessages(roomId) {
    const snap = await db.collection('chat_messages').where('roomId', '==', roomId).get();
    const batch = db.batch();
    snap.docs.forEach(d => batch.delete(d.ref));
    await batch.commit();
  }

  // --- Admin catalog (как AdminRepository.kt) ---
  function mapSubjectDoc(doc, fallbackGroupId) {
    const d = doc.data() || {};
    const name = d.name || d.subject || '';
    const groupId = d.groupId || fallbackGroupId || '';
    const { ids, names } = parseSubjectSemesters(d);
    return {
      id: doc.id,
      name,
      groupId,
      groupName: d.groupName || '',
      semesterIds: ids,
      semesterNames: names
    };
  }

  async function getSubjects(groupId, groupName) {
    if (!groupId && !groupName) return [];
    const all = await getAllSubjects();
    const gn = (groupName || '').trim().toLowerCase();
    return all.filter(s => {
      if (groupId && s.groupId === groupId) return true;
      if (gn && (s.groupName || '').trim().toLowerCase() === gn) return true;
      return false;
    });
  }

  async function getAllSubjects() {
    const snap = await db.collection('admin_subjects').get();
    return snap.docs.map(d => mapSubjectDoc(d, ''))
      .filter(s => s.name && s.groupId)
      .sort((a, b) => (a.groupName || '').localeCompare(b.groupName || '', 'ru') || a.name.localeCompare(b.name, 'ru'));
  }

  async function getSemesters(groupId, groupName) {
    if (!groupId && !groupName) return [];
    const snap = await db.collection('admin_semesters').get();
    const gn = (groupName || '').trim().toLowerCase();
    return snap.docs.map(d => {
      const x = d.data() || {};
      return {
        id: d.id,
        name: x.name || '',
        startDate: x.startDate || '',
        endDate: x.endDate || '',
        groupId: x.groupId || '',
        groupName: x.groupName || ''
      };
    }).filter(s => {
      if (!s.name) return false;
      if (groupId && s.groupId === groupId) return true;
      if (gn && (s.groupName || '').trim().toLowerCase() === gn) return true;
      return false;
    }).sort((a, b) => a.name.localeCompare(b.name, 'ru'));
  }

  async function findSubjectDoc(subjectName, groupId) {
    const name = (subjectName || '').trim();
    if (!name || !groupId) return null;
    const docId = subjectDocumentId(name, groupId);
    const ref = db.collection('admin_subjects').doc(docId);
    const direct = await ref.get();
    if (direct.exists) return { ref, id: direct.id, data: direct.data() };
    const snap = await db.collection('admin_subjects')
      .where('groupId', '==', groupId).where('name', '==', name).limit(1).get();
    if (!snap.empty) {
      const doc = snap.docs[0];
      return { ref: doc.ref, id: doc.id, data: doc.data() };
    }
    return null;
  }

  async function migrateTeacherSubjectIds(oldSid, newSid) {
    if (!oldSid || !newSid || oldSid === newSid) return;
    const teachers = await getCatalogTeachers();
    for (const t of teachers) {
      if (!(t.subjectIds || []).includes(oldSid)) continue;
      const updated = [...new Set((t.subjectIds || []).map(id => id === oldSid ? newSid : id))];
      await updateCatalogTeacher(t.id, t.fullName, updated);
    }
  }

  async function removeSubjectIdFromTeachers(subjectId) {
    if (!subjectId) return;
    const teachers = await getCatalogTeachers();
    for (const t of teachers) {
      if (!(t.subjectIds || []).includes(subjectId)) continue;
      await updateCatalogTeacher(t.id, t.fullName, (t.subjectIds || []).filter(id => id !== subjectId));
    }
  }

  async function migrateSemesterInSubjects(oldId, newId, newName) {
    if (!oldId || !newId || oldId === newId) return;
    const snap = await db.collection('admin_subjects').get();
    const batch = db.batch();
    let has = false;
    snap.docs.forEach(doc => {
      const { ids, names } = parseSubjectSemesters(doc.data());
      if (!ids.includes(oldId)) return;
      const newIds = ids.map(id => id === oldId ? newId : id);
      const newNames = ids.map((id, i) => (id === oldId ? newName : names[i] || ''));
      batch.update(doc.ref, subjectSemestersPayload(newIds, newNames));
      has = true;
    });
    if (has) await batch.commit();
  }

  async function detachSemesterFromAllSubjects(semesterId) {
    if (!semesterId) return;
    const snap = await db.collection('admin_subjects').get();
    const batch = db.batch();
    let has = false;
    snap.docs.forEach(doc => {
      const { ids, names } = parseSubjectSemesters(doc.data());
      if (!ids.includes(semesterId)) return;
      const paired = ids.map((id, i) => [id, names[i] || '']).filter(([id]) => id !== semesterId);
      batch.update(doc.ref, subjectSemestersPayload(paired.map(p => p[0]), paired.map(p => p[1])));
      has = true;
    });
    if (has) await batch.commit();
  }

  async function addSubject(subjectName, groupId, groupName) {
    try {
      const name = (subjectName || '').trim();
      if (!name || !groupId) return false;
      if (await findSubjectDoc(name, groupId)) return false;
      const docId = subjectDocumentId(name, groupId);
      await db.collection('admin_subjects').doc(docId).set({
        name, groupId, groupName: groupName || '',
        ...subjectSemestersPayload([], [])
      });
      return true;
    } catch (e) {
      console.error(e);
      return false;
    }
  }

  async function removeSubject(subjectName, groupId) {
    try {
      const found = await findSubjectDoc(subjectName, groupId);
      if (!found) return false;
      const sid = subjectDocumentId(subjectName, groupId);
      await removeSubjectIdFromTeachers(found.id);
      if (sid !== found.id) await removeSubjectIdFromTeachers(sid);
      await found.ref.delete();
      return true;
    } catch (e) {
      console.error(e);
      return false;
    }
  }

  async function updateSubject(oldName, groupId, newName) {
    try {
      const trimmed = (newName || '').trim();
      if (!trimmed) return false;
      const found = await findSubjectDoc(oldName, groupId);
      if (!found) return false;
      const d = found.data;
      const { ids, names } = parseSubjectSemesters(d);
      const groupNameVal = d.groupName || '';
      const oldSid = found.id;
      const newSid = subjectDocumentId(trimmed, groupId);
      if (oldSid === newSid && (d.name || '').trim() === trimmed) {
        await found.ref.set({
          name: trimmed, groupId, groupName: groupNameVal,
          ...subjectSemestersPayload(ids, names)
        }, { merge: true });
        return true;
      }
      const dup = await db.collection('admin_subjects').doc(newSid).get();
      if (dup.exists && dup.id !== oldSid) return false;
      await found.ref.delete();
      await db.collection('admin_subjects').doc(newSid).set({
        name: trimmed, groupId, groupName: groupNameVal,
        ...subjectSemestersPayload(ids, names)
      });
      await migrateTeacherSubjectIds(oldSid, newSid);
      const legacySid = subjectDocumentId(oldName, groupId);
      if (legacySid !== oldSid && legacySid !== newSid) {
        await migrateTeacherSubjectIds(legacySid, newSid);
      }
      return true;
    } catch (e) {
      console.error(e);
      return false;
    }
  }

  async function assignSubjectSemester(subjectName, groupId, semester) {
    try {
      const found = await findSubjectDoc(subjectName, groupId);
      if (!found) return 'SUBJECT_NOT_FOUND';
      if (semester.groupId && semester.groupId !== groupId) return 'ERROR';
      const { ids, names } = parseSubjectSemesters(found.data);
      if (ids.includes(semester.id)) return 'DUPLICATE';
      await found.ref.update(
        subjectSemestersPayload([...ids, semester.id], [...names, semester.name])
      );
      return 'SUCCESS';
    } catch (e) {
      console.error(e);
      return 'ERROR';
    }
  }

  async function unassignSubjectSemester(subjectName, groupId, semesterId) {
    try {
      const found = await findSubjectDoc(subjectName, groupId);
      if (!found) return 'SUBJECT_NOT_FOUND';
      const { ids, names } = parseSubjectSemesters(found.data);
      if (!ids.includes(semesterId)) return 'ERROR';
      const paired = ids.map((id, i) => [id, names[i] || '']).filter(([id]) => id !== semesterId);
      await found.ref.update(
        subjectSemestersPayload(paired.map(p => p[0]), paired.map(p => p[1]))
      );
      return 'SUCCESS';
    } catch (e) {
      console.error(e);
      return 'ERROR';
    }
  }

  async function addSemester(semesterName, groupId, groupName, startDate, endDate) {
    try {
      const name = (semesterName || '').trim();
      if (!name || !groupId) return false;
      const docId = normalizeAdminId(`${name}_${groupId}`);
      const existing = await db.collection('admin_semesters').doc(docId).get();
      if (existing.exists) return false;
      await db.collection('admin_semesters').doc(docId).set({
        name, startDate: (startDate || '').trim(), endDate: (endDate || '').trim(),
        groupId, groupName: groupName || ''
      });
      return true;
    } catch (e) {
      console.error(e);
      return false;
    }
  }

  async function updateSemester(oldSemester, semesterNumber, startDate, endDate) {
    try {
      if (!oldSemester?.id || semesterNumber <= 0) return false;
      const start = (startDate || '').trim();
      const end = (endDate || '').trim();
      const groupId = oldSemester.groupId;
      if (!start || !end || !groupId) return false;
      const newName = `${semesterNumber} семестр`;
      const oldId = oldSemester.id;
      const newId = normalizeAdminId(`${newName}_${groupId}`);
      if (newId !== oldId) {
        const existing = await db.collection('admin_semesters').doc(newId).get();
        if (existing.exists) return false;
      }
      const payload = {
        name: newName, startDate: start, endDate: end,
        groupId, groupName: oldSemester.groupName || ''
      };
      if (newId === oldId) {
        await db.collection('admin_semesters').doc(oldId).set(payload);
      } else {
        await db.collection('admin_semesters').doc(newId).set(payload);
        await migrateSemesterInSubjects(oldId, newId, newName);
        await db.collection('admin_semesters').doc(oldId).delete();
      }
      return true;
    } catch (e) {
      console.error(e);
      return false;
    }
  }

  async function removeSemester(semesterId) {
    try {
      await detachSemesterFromAllSubjects(semesterId);
      await db.collection('admin_semesters').doc(semesterId).delete();
      return true;
    } catch (e) {
      console.error(e);
      return false;
    }
  }

  async function saveGroupLimits(groupId, limits) {
    const ref = db.collection('group_limits').doc(groupId);
    const t = limits?.teacherLimit;
    const s = limits?.studentLimit;
    const h = limits?.headmanLimit;
    if (t == null && s == null && h == null) {
      await ref.delete();
      return true;
    }
    const data = {};
    if (t != null) data.teacherLimit = t;
    if (s != null) data.studentLimit = s;
    if (h != null) data.headmanLimit = h;
    await ref.set(data, { merge: true });
    return true;
  }

  async function getCatalogTeachers() {
    const snap = await db.collection('admin_teachers').get();
    return snap.docs.map(d => ({
      id: d.id,
      fullName: (d.data().fullName || '').trim(),
      subjectIds: d.data().subjectIds || []
    })).filter(t => t.fullName)
      .sort((a, b) => a.fullName.localeCompare(b.fullName, 'ru'));
  }

  async function addCatalogTeacher(fullName, subjectIds) {
    const name = (fullName || '').trim();
    if (!name) return false;
    const docId = normalizeAdminId(`${name}_${Date.now()}`);
    await db.collection('admin_teachers').doc(docId).set({
      fullName: name,
      subjectIds: (subjectIds || []).filter(Boolean)
    });
    return true;
  }

  async function loadAdminCatalog(groupId, groupName) {
    const [subjects, semesters, allTeachers, limits] = await Promise.all([
      getSubjects(groupId, groupName),
      getSemesters(groupId, groupName),
      getCatalogTeachers(),
      getGroupLimits(groupId)
    ]);
    const ownSubjectIds = new Set(subjects.flatMap(s => {
      const g = s.groupId || groupId;
      return [subjectDocumentId(s.name, g), s.id].filter(Boolean);
    }));
    const rawTeachers = allTeachers.filter(t =>
      !t.subjectIds.length || t.subjectIds.some(sid => ownSubjectIds.has(sid))
    );
    const teachers = mergeTeachersByName(rawTeachers);
    const bindingsCount = teachers.reduce((n, t) => n + (t.subjectIds?.length || 0), 0);
    return {
      groupId, groupName,
      subjects, semesters,
      teachers, rawTeachers,
      limits,
      stats: {
        subjects: subjects.length,
        semesters: semesters.length,
        teachers: teachers.length,
        bindings: bindingsCount
      }
    };
  }

  // --- Club leaders ---
  function mapClubLeader(id, d) {
    d = d || {};
    return {
      id, type: d.type || 'CLUB', teacherId: d.teacherId || '',
      teacherName: d.teacherName || '', groupId: d.groupId || '', groupName: d.groupName || ''
    };
  }

  function belongsToGroup(entry, groupId, groupName) {
    const entryGid = entry.groupId || groupNameToDocumentId(entry.groupName);
    if (entryGid && groupId) return entryGid === groupId;
    return groupName && entry.groupName && entry.groupName.toLowerCase() === groupName.toLowerCase();
  }

  async function getClubLeaders(groupId, groupName) {
    const snap = await db.collection('club_leaders').get();
    return snap.docs.map(d => mapClubLeader(d.id, d.data()))
      .filter(e => belongsToGroup(e, groupId, groupName))
      .sort((a, b) => a.teacherName.localeCompare(b.teacherName, 'ru'));
  }

  async function addClubLeader(entry) {
    await db.collection('club_leaders').add({
      type: entry.type, teacherId: entry.teacherId, teacherName: entry.teacherName,
      groupId: entry.groupId, groupName: entry.groupName
    });
  }

  async function removeClubLeader(id) {
    await db.collection('club_leaders').doc(id).delete();
  }

  async function deleteClub(id) {
    await db.collection('clubs').doc(id).delete();
  }

  async function getAdminGroups() {
    const snap = await db.collection('admin_groups').get();
    return snap.docs.map(d => ({ id: d.id, name: d.data().name || d.id }));
  }

  async function getGroupLimits(groupId) {
    const doc = await db.collection('group_limits').doc(groupId).get();
    return doc.exists ? doc.data() : null;
  }

  function journalLabelDocId(teacherId, groupName, subject, date) {
    const g = (groupName || '').toLowerCase().replace(/[^a-zA-Z0-9_-]/g, '_').replace(/^_|_$/g, '') || 'group';
    const s = (subject || '').trim().toLowerCase().replace(/[^a-zA-Z0-9а-яё_-]/gi, '_') || 'subject';
    return `${teacherId}_${g}_${s}_${date.replace(/\./g, '_')}`;
  }

  async function getJournalLabels(teacherId, groupName, subject, dates) {
    if (!dates.length) return {};
    const map = {};
    for (const date of dates) {
      const id = journalLabelDocId(teacherId, groupName, subject, date);
      const doc = await db.collection('journal_column_labels').doc(id).get();
      if (doc.exists) {
        const t = doc.data().lessonType || '';
        if (t) map[date] = t;
      }
    }
    return map;
  }

  async function setJournalLabel(teacherId, groupName, subject, date, lessonType) {
    const id = journalLabelDocId(teacherId, groupName, subject, date);
    if (!lessonType) return db.collection('journal_column_labels').doc(id).delete();
    await db.collection('journal_column_labels').doc(id).set({
      teacherId, groupName, subject, date, lessonType
    });
  }

  async function updateChatMessage(id, text) {
    await db.collection('chat_messages').doc(id).update({ text: text.trim() });
  }

  async function unpinChatMessage(roomId) {
    await db.collection('chat_rooms').doc(roomId).update({
      pinnedMessageId: firebase.firestore.FieldValue.delete(),
      pinnedMessageText: firebase.firestore.FieldValue.delete(),
      pinnedByName: firebase.firestore.FieldValue.delete()
    });
  }

  async function getCatalogGroups() {
    const snap = await db.collection('admin_groups').get();
    return snap.docs.map(d => d.data().name || d.id).filter(Boolean).sort((a, b) => a.localeCompare(b, 'ru'));
  }

  return {
    init, get auth() { return auth; },
    fetchUser, registerUser, updatePersonalInfo, deletePersonalData, getGroupStudents, canRegisterToGroup,
    getCatalogGroups,
    getStudentGrades, getTeacherGrades, getGradesForStudents, saveGrade, deleteGrade,
    getStudentAbsences, getGroupAbsences, saveAbsence, deleteAbsence,
    getScheduleForGroup, subscribeSchedule, saveSchedule, deleteSchedule,
    getGroupEvents, saveEvent, deleteEvent,
    getMealSubscription, setMealSubscription, getMealSubscribers, setMealAutoPlan,
    applyWeeklyMealPlan, ensureWeeklyMealAutoPlan,
    getClubsForGroup, saveClub, deleteClub, toggleClubParticipant,
    getNotifications, getUnreadCount, subscribeNotifications, markNotificationRead, markAllNotificationsRead,
    deleteNotification, deleteAllNotifications, createNotification,
    buildGroupRoomId, ensureChatRoom, subscribeMessages, subscribeRoom,
    sendChatMessage, sendChatSticker, deleteChatMessage, updateChatMessage,
    pinChatMessage, unpinChatMessage, clearChatMessages,
    getSemesters, getSubjects, getGroupLimits, loadAdminCatalog,
    addSubject, removeSubject, updateSubject, assignSubjectSemester, unassignSubjectSemester,
    addSemester, updateSemester, removeSemester, saveGroupLimits,
    getClubLeaders, addClubLeader, removeClubLeader,
    getCatalogTeachers, addCatalogTeacher, removeCatalogTeacher, updateCatalogTeacher, updateTeachersByName,
    getJournalLabels, setJournalLabel
  };
})();

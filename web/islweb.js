/* ISL BLADE · versão web
   Faz o papel das "pontes" do app Android (ISLAccount, ISLCommunity, ISLLinks, ISLMaps)
   usando o Firebase no navegador, para o mesmo HTML funcionar no iPhone e no computador. */
import { initializeApp } from 'https://www.gstatic.com/firebasejs/10.14.1/firebase-app.js';
import { getAuth, GoogleAuthProvider, signInWithPopup, signInWithRedirect, getRedirectResult, onAuthStateChanged, signOut, reauthenticateWithPopup, deleteUser } from 'https://www.gstatic.com/firebasejs/10.14.1/firebase-auth.js';
import { getFirestore, doc, getDoc, setDoc, deleteDoc, addDoc, collection, query, orderBy, limit, where, onSnapshot, serverTimestamp, getDocs } from 'https://www.gstatic.com/firebasejs/10.14.1/firebase-firestore.js';

const app = initializeApp(window.ISL_FIREBASE_CONFIG);
const auth = getAuth(app);
auth.languageCode = 'pt';
const db = getFirestore(app);
const provider = new GoogleAuthProvider();
const CHECKIN_MS = 3 * 60 * 60 * 1000;

const send = (name, detail) => window.dispatchEvent(new CustomEvent(name, { detail }));
const ms = v => (v && typeof v.toMillis === 'function') ? v.toMillis() : (typeof v === 'number' ? v : Date.now());
const errOf = e => ({ message: String((e && (e.code || e.message)) || '') .replace('permission-denied', 'PERMISSION_DENIED') });
const nameOf = u => u.displayName || 'Patinador ISL';
const photoOf = u => u.photoURL || '';
const clean = (v, depth = 0) => {
  if (depth > 4 || v == null) return null;
  if (typeof v === 'string') return v.slice(0, 300);
  if (typeof v === 'number' || typeof v === 'boolean') return v;
  if (Array.isArray(v)) return v.slice(0, 500).map(x => clean(x, depth + 1)).filter(x => x != null);
  if (typeof v === 'object') { const o = {}; Object.keys(v).slice(0, 60).forEach(k => { const x = clean(v[k], depth + 1); if (x != null) o[k] = x; }); return o; }
  return null;
};
const isMobile = /iPhone|iPad|iPod|Android/i.test(navigator.userAgent);

function authState() {
  const u = auth.currentUser;
  send('isl-auth', u ? { signedIn: true, uid: u.uid, name: u.displayName, email: u.email, photo: photoOf(u) } : { signedIn: false });
}

async function createUserDoc(u) {
  try {
    const ref = doc(db, 'users', u.uid);
    const snap = await getDoc(ref);
    const novo = !snap.exists();
    const base = { name: u.displayName, email: u.email, photo: photoOf(u), lastLogin: serverTimestamp() };
    if (novo) base.createdAt = serverTimestamp();
    await setDoc(ref, base, { merge: true });
    send('isl-cloud', novo ? { ok: true, created: true } : { ok: true });
    if (!novo) send('isl-profile', snap.data());
  } catch (e) { send('isl-cloud', { ok: false }); }
}

let lastUid = null;
onAuthStateChanged(auth, u => {
  authState();
  if (u && u.uid !== lastUid) createUserDoc(u);
  lastUid = u ? u.uid : null;
});
getRedirectResult(auth).catch(e => send('isl-auth-error', { code: e.code === 'auth/popup-closed-by-user' ? 12501 : 0, message: e.code || '' }));

/* ---------- Conta ---------- */
window.ISLAccount = {
  signIn() {
    // popup também no celular: o login por redirecionamento falha em navegadores que bloqueiam cookies de terceiros
    const go = signInWithPopup(auth, provider);
    Promise.resolve(go).catch(e => {
      if (e.code === 'auth/popup-blocked') return signInWithRedirect(auth, provider);
      send('isl-auth-error', { code: (e.code === 'auth/popup-closed-by-user' || e.code === 'auth/cancelled-popup-request') ? 12501 : (e.code === 'auth/network-request-failed' ? 7 : 0), message: e.code || '' });
    });
  },
  signOut() { stopCommunity(); signOut(auth).then(authState); },
  getAccount() { authState(); },
  saveProfile(json) {
    const u = auth.currentUser; if (!u) return;
    try {
      const o = JSON.parse(json), m = {};
      ['nickname', 'city', 'bio'].forEach(k => { if (k in o) m[k] = String(o[k] || '').trim(); });
      m.updatedAt = serverTimestamp();
      setDoc(doc(db, 'users', u.uid), m, { merge: true }).then(() => send('isl-cloud', { ok: true, saved: true })).catch(() => send('isl-cloud', { ok: false, saved: false }));
    } catch (e) { send('isl-cloud', { ok: false }); }
  },
  publishProfile(json) {
    const u = auth.currentUser; if (!u) return;
    try {
      const m = clean(JSON.parse(json)) || {};
      m.uid = u.uid; m.name = nameOf(u); m.photo = photoOf(u); m.updatedAt = Date.now();
      setDoc(doc(db, 'profiles', u.uid), m).catch(() => {});
    } catch (e) {}
  },
  async deleteAccount() {
    const u = auth.currentUser; if (!u) { send('isl-account-deleted', { ok: false, reason: 'auth' }); return; }
    const uid = u.uid; stopCommunity();
    const delWhere = async col => { try { const s = await getDocs(query(collection(db, col), where('uid', '==', uid))); await Promise.all(s.docs.map(d => deleteDoc(d.ref))); } catch (e) {} };
    await Promise.allSettled([delWhere('posts'), delWhere('went'), deleteDoc(doc(db, 'checkins', uid)), deleteDoc(doc(db, 'profiles', uid)), deleteDoc(doc(db, 'users', uid))]);
    let ok = false;
    try { await deleteUser(u); ok = true; }
    catch (e) {
      if (e.code === 'auth/requires-recent-login') { try { await reauthenticateWithPopup(u, provider); await deleteUser(u); ok = true; } catch (x) {} }
    }
    try { await signOut(auth); } catch (e) {}
    send('isl-account-deleted', ok ? { ok: true } : { ok: false, reason: 'auth-delete' });
    authState();
  }
};

/* ---------- Comunidade ---------- */
let unsubPosts = null, unsubCheckins = null, unsubWent = null;
function stopCommunity() { [unsubPosts, unsubCheckins, unsubWent].forEach(f => f && f()); unsubPosts = unsubCheckins = unsubWent = null; }
function startCommunity() {
  if (!auth.currentUser) { send('isl-community-error', { code: 'auth' }); return; }
  const fail = e => send('isl-community-error', errOf(e));
  if (!unsubPosts) unsubPosts = onSnapshot(query(collection(db, 'posts'), orderBy('createdAt', 'desc'), limit(60)), s => {
    send('isl-posts', s.docs.map(d => { const x = d.data(); return { id: d.id, uid: x.uid || '', name: x.name || '', photo: x.photo || '', type: x.type || '', text: x.text || '', createdAt: ms(x.createdAt), data: x.data || {} }; }));
  }, fail);
  if (!unsubCheckins) unsubCheckins = onSnapshot(query(collection(db, 'checkins'), where('expiresAt', '>', Date.now() - CHECKIN_MS)), s => {
    send('isl-checkins', s.docs.map(d => { const x = d.data(); return { uid: d.id, name: x.name || '', photo: x.photo || '', parkKey: x.parkKey || '', parkName: x.parkName || '', city: x.city || '', uf: x.uf || '', expiresAt: ms(x.expiresAt), at: ms(x.at) }; }));
  }, fail);
  if (!unsubWent) unsubWent = onSnapshot(query(collection(db, 'went'), limit(5000)), s => {
    send('isl-went', s.docs.map(d => { const x = d.data(); return { eventKey: x.eventKey || '', uid: x.uid || '', name: x.name || '', photo: x.photo || '', title: x.title || '' }; }));
  }, fail);
}
function addPost(u, type, text, data) {
  return addDoc(collection(db, 'posts'), { uid: u.uid, name: nameOf(u), photo: photoOf(u), type, text: text.slice(0, 200), data: clean(data) || {}, createdAt: serverTimestamp() })
    .then(() => send('isl-community-ok', { action: 'post' }))
    .catch(e => send('isl-community-error', errOf(e)));
}
window.ISLCommunity = {
  start: startCommunity,
  stop: stopCommunity,
  post(json) {
    const u = auth.currentUser; if (!u) { send('isl-community-error', { code: 'auth' }); return; }
    try { const o = JSON.parse(json); const text = String(o.text || '').trim(); const type = String(o.type || '');
      if (!text || !['trick', 'challenge', 'combo', 'robot'].includes(type)) return; addPost(u, type, text, o.data);
    } catch (e) { send('isl-community-error', errOf(e)); }
  },
  deletePost(id) { deleteDoc(doc(db, 'posts', String(id))).catch(e => send('isl-community-error', errOf(e))); },
  checkIn(json) {
    const u = auth.currentUser; if (!u) { send('isl-community-error', { code: 'auth' }); return; }
    try { const o = JSON.parse(json); const m = { name: nameOf(u), photo: photoOf(u), at: serverTimestamp(), expiresAt: Date.now() + CHECKIN_MS };
      ['parkKey', 'parkName', 'city', 'uf'].forEach(k => m[k] = String(o[k] || ''));
      setDoc(doc(db, 'checkins', u.uid), m).then(() => send('isl-community-ok', { action: 'checkin' })).catch(e => send('isl-community-error', errOf(e)));
      if (o.share !== false) addPost(u, 'checkin', `Estou na pista ${m.parkName} (${m.city} · ${m.uf})`, { parkKey: m.parkKey, parkName: m.parkName });
    } catch (e) { send('isl-community-error', errOf(e)); }
  },
  checkOut() { const u = auth.currentUser; if (!u) return; deleteDoc(doc(db, 'checkins', u.uid)).then(() => send('isl-community-ok', { action: 'checkout' })); },
  markWent(json) {
    const u = auth.currentUser; if (!u) { send('isl-community-error', { code: 'auth' }); return; }
    try { const o = JSON.parse(json); const key = String(o.eventKey || '').replace(/[^A-Za-z0-9_-]/g, ''); if (!key) return;
      setDoc(doc(db, 'went', key + '__' + u.uid), { eventKey: key, uid: u.uid, name: nameOf(u), photo: photoOf(u), title: String(o.title || '').slice(0, 120), at: serverTimestamp() })
        .then(() => send('isl-community-ok', { action: 'went' })).catch(e => send('isl-community-error', errOf(e)));
      if (o.share) addPost(u, 'event', `Tô dentro: ${o.title || ''}!`, { eventKey: key });
    } catch (e) { send('isl-community-error', errOf(e)); }
  },
  unmarkWent(key) { const u = auth.currentUser; if (!u) return; const k = String(key || '').replace(/[^A-Za-z0-9_-]/g, ''); deleteDoc(doc(db, 'went', k + '__' + u.uid)); },
  getProfile(uid) {
    const id = String(uid || '').replace(/[^A-Za-z0-9]/g, ''); if (!id) return;
    if (!auth.currentUser) { send('isl-user-profile', { uid: id, error: true }); return; }
    getDoc(doc(db, 'profiles', id)).then(s => send('isl-user-profile', { uid: id, exists: s.exists(), profile: s.exists() ? s.data() : undefined }))
      .catch(() => send('isl-user-profile', { uid: id, error: true }));
  }
};

/* ---------- Links e mapa ---------- */
window.ISLLinks = { open(url) { if (/^https:\/\//i.test(url)) window.open(url, '_blank', 'noopener'); } };
window.ISLMaps = {
  openMap(url) { window.open(url, '_blank', 'noopener'); },
  locate() {
    if (!navigator.geolocation) { window.hubMapMessage && window.hubMapMessage('Este navegador não informa a localização.'); return; }
    navigator.geolocation.getCurrentPosition(p => window.hubMapLocated && window.hubMapLocated(p.coords.latitude, p.coords.longitude),
      () => window.hubMapMessage && window.hubMapMessage('Não foi possível obter a localização. Permita o acesso à localização no navegador.'), { enableHighAccuracy: true, timeout: 15000 });
  }
};
window.dispatchEvent(new Event('isl-web-ready'));
authState();

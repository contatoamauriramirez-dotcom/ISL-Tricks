"""Gera a versão web do ISL BLADE em docs/app/ a partir de 02_VERSAO_WEB/ISL_BLADE.html.
Injeta a configuração web do Firebase e o islweb.js (pontes Android -> Firebase no navegador)."""
import os, shutil, json
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
src = os.path.join(ROOT, '02_VERSAO_WEB', 'ISL_BLADE.html')
out = os.path.join(ROOT, 'docs', 'app')
os.makedirs(out, exist_ok=True)
cfg = {"apiKey": "AIzaSyBkYtraY-WLs2k8k2Jw2e7mDWAijKKuX9s", "authDomain": "isl-blade.firebaseapp.com", "projectId": "isl-blade",
       "storageBucket": "isl-blade.firebasestorage.app", "messagingSenderId": "546286905575", "appId": "1:546286905575:web:2a92e2d0284d5cf93bda42"}
html = open(src, encoding='utf-8').read()
head = ('<meta name="description" content="ISL BLADE: manobras de patins agressivo por nível, desafios semanais, jogos e pistas do Brasil.">'
        '<link rel="icon" href="icon.png"><link rel="apple-touch-icon" href="icon.png"><meta name="theme-color" content="#131215">'
        '<style>html.nl-badge{--bottom-nav-height:130px}html.nl-badge .foot{padding-bottom:max(64px,env(safe-area-inset-bottom,0px))!important;align-items:flex-start}</style>'
        '<script>(function(){function c(){if(document.getElementById("nl-badge-frame")){document.documentElement.classList.add("nl-badge");return true}}if(!c()){var o=new MutationObserver(function(){if(c())o.disconnect()});document.addEventListener("DOMContentLoaded",function(){c()||o.observe(document.body,{childList:true})})}})();</script>'
        f'<script>window.ISL_FIREBASE_CONFIG={json.dumps(cfg)};</script><script type="module" src="islweb.js"></script>')
assert html.count('</head>') == 1
html = html.replace('</head>', head + '</head>')
open(os.path.join(out, 'index.html'), 'w', encoding='utf-8').write(html)
shutil.copy(os.path.join(ROOT, 'web', 'islweb.js'), os.path.join(out, 'islweb.js'))
shutil.copy(os.path.join(ROOT, 'docs', 'loja', 'icone_512.png'), os.path.join(out, 'icon.png'))
print('ok', os.path.getsize(os.path.join(out, 'index.html')))

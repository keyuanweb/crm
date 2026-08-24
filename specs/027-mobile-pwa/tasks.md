# Tasks: 绉诲姩绔?PWA

**Input**: Design documents from `/specs/027-mobile-pwa/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/pwa.md

**Tests**: 绔犵▼鍘熷垯鍥涜姹傛祴璇曞厛浜庡疄鐜帮紙绾⑩啋缁匡級锛屾湰鍔熻兘鍚墠绔粍浠舵祴璇曘€?
## Phase 1: PWA 鍩虹璁炬柦锛圲S1 鍙畨瑁咃級

- [x] T001 [P] [US1] 鍓嶇锛氭柊澧?`frontend/public/manifest.webmanifest`鈥斺€攏ame/short_name/display=standalone/start_url/theme_color=#1677ff/icons(192/512)銆?- [x] T002 [P] [US1] 鍓嶇锛氱敓鎴?`frontend/public/icon-192.png`銆乣icon-512.png`銆乣apple-touch-icon.png`锛堢畝鍗曞搧鐗屽浘鏍囷紝鍙敤绯荤粺棣栧瓧姣嶆垨绠€鍗曞浘褰級銆?- [x] T003 [P] [US1] 鍓嶇锛歚index.html` 鍔?manifest link + theme-color + iOS 鍏冩暟鎹紙apple-mobile-web-app-capable銆乻tatus-bar銆乤pple-touch-icon锛夈€?
## Phase 2: Service Worker锛圲S2 绂荤嚎锛?
- [x] T004 [P] [US2] 鍓嶇锛氭柊澧?`frontend/public/sw.js`鈥斺€擵ERSION 甯搁噺锛沬nstall 棰勭紦瀛樺簲鐢ㄥ澹筹紙"/"銆乮ndex.html銆乵anifest銆佸浘鏍囷級锛沠etch 缂撳瓨浼樺厛+缃戠粶鍥為€€锛堜笉缂撳瓨 /api/**锛夛紱activate 娓呯悊鏃х紦瀛樸€?- [x] T005 [US2] 鍓嶇锛歚main.tsx` 鐢熶骇鐜锛坕mport.meta.env.PROD锛夋敞鍐?`/sw.js`锛沗src/test/setup.ts` mock navigator.serviceWorker 閬垮厤娴嬭瘯鎶ラ敊銆?
## Phase 3: 瀹夎鎻愮ず涓庡揩鎹峰叆鍙ｏ紙US1/US3锛?
- [x] T006 [P] [US1] 鍓嶇锛氭柊澧?`src/components/InstallPrompt.tsx`鈥斺€旂洃鍚?beforeinstallprompt锛岃Е鍙戞椂鏄剧ず"瀹夎鍒颁富灞忓箷"鎻愮ず锛堝彲鍏抽棴锛夛紱appinstalled 鍚庨殣钘忋€傜粍浠舵祴璇曪細浜嬩欢瑙﹀彂鏄剧ず/涓嶈Е鍙戦殣钘忋€?- [x] T007 [US3] 鍓嶇锛歚DashboardPage.tsx` isMobile 鏃舵覆鏌撳鍕ゅ揩鎹峰叆鍙ｆ潯锛堝鎴?璁拌窡杩?绾跨储澶ф寜閽?鈮?4px锛岀偣鍑诲鑸級锛涚粍浠舵祴璇曪細isMobile 娓叉煋蹇嵎鍏ュ彛銆?
## Phase 4: 楠岃瘉涓庢敹灏?
- [x] T008 鍓嶇锛歚pnpm run typecheck` + `lint` + `test` 鍏ㄩ噺閫氳繃锛堟柊澧炵粍浠舵祴璇曪級銆?- [x] T009 [P] 鎵嬪姩鍐掔儫锛歭ocalhost 鎵撳紑 鈫?devtools Application 鈫?Manifest 鏍￠獙閫氳繃锛涜Е鍙戝畨瑁呮彁绀猴紱鏂綉鍒锋柊搴旂敤澶栧３鍙姞杞斤紙鐢熶骇鏋勫缓楠岃瘉锛夛紱妗岄潰绔姛鑳芥棤鍥炲綊銆?
## Dependencies & Execution Order

- T001/T002/T003 鍙苟琛岋紙US1 鍩虹璁炬柦锛夈€?- T004 鏃犱緷璧栵紱T005 渚濊禆 T004銆?- T006 鏃犱緷璧栵紱T007 鏃犱緷璧栵紙鍧囧墠绔粍浠讹級銆?- Phase 4 鍦ㄦ墍鏈夊疄鐜板畬鎴愬悗鎵ц銆?
## Notes

- 绾墠绔紱鏃犲悗绔敼鍔ㄣ€?- SW 鐢ㄥ師鐢?API锛堜笉寮?workbox锛夛紱绂荤嚎浠呭簲鐢ㄥ澹炽€?- 寮€鍙戠幆澧冧笉娉ㄥ唽 SW锛涚敓浜ф瀯寤哄悗鐢熸晥銆?- PWA 浠?HTTPS/localhost銆?
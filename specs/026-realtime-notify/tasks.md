# Tasks: 瀹炴椂閫氱煡鎺ㄩ€?
**Input**: Design documents from `/specs/026-realtime-notify/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/realtime-notify.md

**Tests**: 绔犵▼鍘熷垯鍥涜姹傛祴璇曞厛浜庡疄鐜帮紙绾⑩啋缁匡級锛屾湰鍔熻兘鍚悗绔崟鍏?闆嗘垚 + 鍓嶇 hook 娴嬭瘯銆?
## Phase 1: 鍚庣娴嬭瘯鍏堣锛圱DD 绾級

- [x] T001 [P] [US1] 鍚庣锛氱紪鍐?`backend/src/test/java/com/crm/ws/NotificationWebSocketHandlerTest.java` 鍗曞厓娴嬭瘯鈥斺€旇鐩栵細浼氳瘽娉ㄥ唽/娉ㄩ攢銆乶otifyUser 鎺ㄩ€佺粰鐩爣鐢ㄦ埛鍏ㄩ儴浼氳瘽銆佹棤浼氳瘽鏃跺畨鍏ㄨ烦杩囥€佹柇寮€娓呯悊鏄犲皠銆傛鏃?handler 鏈疄鐜帮紝娴嬭瘯缂栬瘧澶辫触锛堢孩锛夈€?- [x] T002 [P] [US1] 鍚庣锛氱紪鍐?`backend/src/test/java/com/crm/integration/RealtimeNotifyIT.java` 闆嗘垚娴嬭瘯鈥斺€斿垱寤洪€氱煡锛坣otify锛夊悗 handler 鏀跺埌鎺ㄩ€佽皟鐢紙mock handler 楠岃瘉瑙﹀彂锛夈€傛鏃舵湭瀹炵幇锛屾祴璇曞け璐ワ紙绾級銆?
## Phase 2: 鍚庣瀹炵幇

- [x] T003 [P] 鍚庣锛歚pom.xml` 鍔?`spring-boot-starter-websocket` 渚濊禆銆?- [x] T004 [US1] 鍚庣锛氭柊澧?`ws/NotificationPushPayload.java`锛坕d/type/message/unreadCount锛夈€?- [x] T005 [US1] 鍚庣锛氭柊澧?`ws/NotificationWebSocketHandler.java`鈥斺€擿ConcurrentHashMap<Long, Set<Session>>` 鏄犲皠锛沘fterConnectionEstablished锛堝瓨 userId锛夊姞鍏ャ€乤fterConnectionClosed 娓呯悊锛沗notifyUser(userId, payload)` 搴忓垪鍖?JSON 鎺ㄩ€佺粰璇ョ敤鎴峰叏閮ㄤ細璇濓紙浼氳瘽鍏抽棴璺宠繃锛夈€傦紙渚濊禆 T004锛?- [x] T006 [US1] 鍚庣锛氭柊澧?`config/WebSocketConfig.java`鈥斺€旀敞鍐?handler 鍒?`/ws/notifications`锛屾彙鎵嬫嫤鎴櫒瑙ｆ瀽 query `token`锛圝wtUtil 楠岃瘉锛夆啋 鎻愬彇 userId 瀛?session attributes锛屽け璐ユ嫆缁濇彙鎵嬨€傦紙渚濊禆 T005锛?- [x] T007 [US1] 鍚庣锛歚NotificationService.notify()` 娉ㄥ叆 handler锛宨nsert 鍚庤皟鐢?`notifyUser(userId, payload)`锛坧ayload.unreadCount 鐢?unreadCount(userId)锛夈€傦紙渚濊禆 T005锛?
## Phase 3: 鍓嶇

- [x] T008 [P] [US1] 鍓嶇锛氭柊澧?`hooks/useNotificationSocket.ts`鈥斺€旇繛鎺?`ws://host/ws/notifications?token=`銆乷nmessage 瑙ｆ瀽鍥炶皟銆佹柇绾挎寚鏁伴€€閬块噸杩烇紙1s鈫?0s锛夈€佽繛鎺ュけ璐ラ檷绾ц疆璇紙setInterval 30s fetchUnreadCount锛夈€佸嵏杞芥竻鐞嗐€?- [x] T009 [US1] 鍓嶇锛歚components/NotificationCenter.tsx` 鐢?hook 鍗虫椂鏇存柊鏈瑙掓爣锛堟敹鍒版秷鎭?setUnread(unreadCount)锛夛紝淇濈暀杞闄嶇骇銆傦紙渚濊禆 T008锛?- [x] T010 [P] [US1] 鍓嶇锛氭柊澧?`hooks/useNotificationSocket.test.ts`鈥斺€攎ock WebSocket锛岄獙璇佽繛鎺?鏀舵秷鎭洿鏂?鏂嚎閲嶈繛/闄嶇骇杞銆?
## Phase 4: 楠岃瘉涓庢敹灏?
- [x] T011 鍚庣锛歚mvn test` 鍏ㄩ噺閫氳繃锛堟柊澧?handler/闆嗘垚娴嬭瘯锛屼笉褰卞搷鏃㈡湁 237锛夈€?- [x] T012 鍓嶇锛歚pnpm run typecheck` + `lint` + `test` 鍏ㄩ噺閫氳繃銆?- [x] T013 [P] 鎵嬪姩鍐掔儫锛氬弻璐﹀彿鐧诲綍锛孉 瑙﹀彂閫氱煡锛孉 瑙掓爣鍗虫椂鏇存柊銆丅 鏃犳劅锛涙柇缃戦噸杩炴仮澶嶃€?
## Dependencies & Execution Order

- T001/T002 鍙苟琛岋紝鍧囦负绾㈤樁娈点€?- T003 鏃犱緷璧栵紱T004 鏃犱緷璧栵紱T005 渚濊禆 T004锛汿006 渚濊禆 T005锛汿007 渚濊禆 T005銆?- T008/T010 鍙苟琛岋紙鍓嶇锛夛紱T009 渚濊禆 T008銆?- Phase 4 鍦ㄦ墍鏈夊疄鐜板畬鎴愬悗鎵ц銆?
## Notes

- 鐢?Spring WebSocket starter锛屽墠绔師鐢?WebSocket API锛圷AGNI 涓嶅紩 socket.io锛夈€?- JWT 缁?query token 璁よ瘉鎻℃墜锛涗細璇濇槧灏勫唴瀛橈紙鍗曞疄渚嬶級銆?- 鎺ㄩ€佽交閲?JSON锛涘垪琛?宸茶浠嶈蛋 REST銆?- 杞闄嶇骇淇濊瘉鍏煎鎬с€?
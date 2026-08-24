# Tasks: 鎵归噺瀵煎叆澧炲己锛堣仈绯讳汉瀵煎叆 + 绾跨储瀵煎叆 UI/璇勫垎锛?
**Input**: Design documents from `/specs/024-bulk-import/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/bulk-import.md

**Tests**: 绔犵▼鍘熷垯鍥涜姹傛祴璇曞厛浜庡疄鐜帮紙绾⑩啋缁匡級銆?
**Note**: 绾跨储瀵煎叆鍚庣锛圠eadExcelService + 绔偣锛夊凡鍦?016 瀹炵幇锛屾湰 feature 鑱氱劍锛氣憼 鏂板鑱旂郴浜哄鍏ワ紙鍚庣+鍓嶇锛夛紱鈶?绾跨储瀵煎叆鎺ュ叆 019 鑷姩璇勫垎锛堢幇鏈夊鍏ラ粯璁?score=0锛夛紱鈶?绾跨储/鑱旂郴浜哄垪琛ㄩ〉琛ュ鍏ヤ笌妯℃澘涓嬭浇鎸夐挳銆?
## Phase 1: 鍚庣娴嬭瘯鍏堣锛圱DD 绾級

- [x] T001 [P] [US2] 鍚庣锛氱紪鍐?`backend/src/test/java/com/crm/service/ContactExcelServiceTest.java` 鍗曞厓娴嬭瘯鈥斺€旇鐩栵細鎸夊鎴峰悕绉板尮閰嶆垚鍔熷鍏ャ€佸鎴蜂笉瀛樺湪澶辫触銆佺己蹇呭～澶辫触銆侀潪 xlsx 鎷掔粷銆傛鏃?ContactExcelService 鏈疄鐜帮紝娴嬭瘯缂栬瘧澶辫触锛堢孩锛夈€?- [x] T002 [P] [US1] 鍚庣锛氱紪鍐?`backend/src/test/java/com/crm/integration/BulkImportIT.java` 闆嗘垚娴嬭瘯鈥斺€斾笂浼犺仈绯讳汉 xlsx 瀵煎叆鎴愬姛杩斿洖璁℃暟锛涗笅杞借仈绯讳汉妯℃澘杩斿洖 xlsx锛涚嚎绱㈠鍏ュ悗 score 闈炵┖锛?19 澧炲己锛夈€傛鏃舵帴鍙ｆ湭瀹炵幇锛屾祴璇曞け璐ワ紙绾級銆?
## Phase 2: 鍚庣瀹炵幇锛圲S2 鑱旂郴浜哄鍏ワ級

- [x] T003 [US2] 鍚庣锛氭柊澧?`service/ContactExcelService.java`鈥斺€擿importContacts(InputStream)`锛圥OI 瑙ｆ瀽锛氬鍚?瀹㈡埛鍚嶇О蹇呭～锛屽鎴锋寜鍚嶇О绮剧‘鍖归厤 customerId锛屼笉鍖归厤澶辫触"瀹㈡埛涓嶅瓨鍦?锛宑reated_by=褰撳墠鐢ㄦ埛锛岃繑鍥?ImportResult + 瀹¤锛夛紱`generateTemplate()`锛坸lsx 琛ㄥご锛氬鍚?瀹㈡埛鍚嶇О/鑱屼綅/鐢佃瘽/閭/瑙掕壊/澶囨敞锛夈€傦紙渚濊禆 CustomerMapper锛?- [x] T004 [US2] 鍚庣锛歚ContactController` 鏂板 `POST /contacts/import`锛坢ultipart锛? `GET /contacts/import-template`銆傦紙渚濊禆 T003锛?
## Phase 3: 鍚庣澧炲己锛圲S1 绾跨储璇勫垎锛?
- [x] T005 [US1] 鍚庣锛歚LeadExcelService` 娉ㄥ叆 `LeadScoreService`锛屽鍏ユ椂鑻ヨ鏃犺瘎鍒嗗垯鑷姩璁＄畻锛?19锛夛紝鏇夸唬鐜版湁榛樿 0銆傦紙渚濊禆 019锛?
## Phase 4: 鍓嶇

- [x] T006 [P] [US1] 鍓嶇锛歚types/importResult.ts`锛沗services/leadService.ts` 鍔?`importLeads`/`downloadLeadTemplate`锛沗services/contactService.ts` 鍔?`importContacts`/`downloadContactTemplate`銆?- [x] T007 [US1] 鍓嶇锛歚LeadListPage` toolbar 鍔?瀵煎叆/涓嬭浇妯℃澘"鎸夐挳锛圲pload + 缁撴灉鍙嶉 Modal锛夛紱`ContactListPage` 鍚岀悊銆?
## Phase 5: 楠岃瘉涓庢敹灏?
- [x] T008 鍚庣锛歚mvn test` 鍏ㄩ噺閫氳繃锛堟柊澧?ContactExcelServiceTest + BulkImportIT锛屼笉褰卞搷鏃㈡湁 230锛夈€?- [x] T009 鍓嶇锛歚pnpm run typecheck` + `lint` + `test` 鍏ㄩ噺閫氳繃銆?- [x] T010 [P] 鎵嬪姩鍐掔儫锛氳仈绯讳汉椤典笅杞芥ā鏉库啋濉啓鈫掍笂浼犫啋缁撴灉鍙嶉锛涚嚎绱㈤〉瀵煎叆鍚庤瘎鍒嗛潪绌恒€?
## Dependencies & Execution Order

- T001/T002 鍙苟琛岋紝鍧囦负绾㈤樁娈点€?- T003 渚濊禆鏃狅紱T004 渚濊禆 T003锛汿005 渚濊禆 019锛堝凡瀹屾垚锛夈€?- T006/T007 鍙苟琛岋紙鍓嶇锛夈€?- Phase 5 鍦ㄦ墍鏈夊疄鐜板畬鎴愬悗鎵ц銆?
## Notes

- 澶嶇敤 016 瀹㈡埛/绾跨储瀵煎叆鐨?POI + ImportResult 妯″紡銆?- 鑱旂郴浜烘寜瀹㈡埛鍚嶇О绮剧‘鍖归厤锛堜笉鍖哄垎澶у皬鍐欙級銆?- 绾跨储瀵煎叆澧炲己锛氭棤璇勫垎琛岃嚜鍔ㄨ绠楋紙019锛夛紝鏈夎瘎鍒嗕繚鐣欍€?
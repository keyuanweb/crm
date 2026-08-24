# Tasks: AI 鏅鸿兘鍔╂墜锛堣鍒欏瀷鏅鸿兘寤鸿锛?
**Input**: Design documents from `/specs/022-ai-assistant/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/smart-suggestions.md

**Tests**: 绔犵▼鍘熷垯鍥涜姹傛祴璇曞厛浜庡疄鐜帮紙绾⑩啋缁匡級锛屾湰鍔熻兘鍚悗绔崟鍏?闆嗘垚 + 鍓嶇娓叉煋娴嬭瘯銆?
## Phase 1: 鍩虹璁炬柦

- [x] T001 [P] 鍚庣锛氭柊澧?`dto/suggestion/SmartSuggestion.java`锛坱ype/title/reason/priority/entityType/entityId/action锛? `dto/suggestion/SuggestionSummary.java`锛堝洓绫昏鏁帮級銆?
## Phase 2: 鍚庣娴嬭瘯鍏堣锛圱DD 绾級

- [x] T002 [P] [US1] 鍚庣锛氱紪鍐?`backend/src/test/java/com/crm/service/SuggestionServiceTest.java` 鍗曞厓娴嬭瘯鈥斺€旇鐩栵細鍥涚被瑙勫垯鑱氬悎銆佷紭鍏堢骇鎺掑簭锛堟祦澶?鍋滄粸>寰呰窡杩?楂樺垎绾跨储锛夈€佸悓瀹炰綋鍘婚噸鍙栨渶楂樹紭鍏堢骇銆佸拷鐣ヨ繃婊ゃ€佷笂闄愭埅鏂€傛鏃?SuggestionService 鏈疄鐜帮紝娴嬭瘯缂栬瘧澶辫触锛堢孩锛夈€?- [x] T003 [P] [US1] 鍚庣锛氱紪鍐?`backend/src/test/java/com/crm/integration/SmartSuggestionIT.java` 闆嗘垚娴嬭瘯鈥斺€擥ET /suggestions 杩斿洖鍒楄〃銆丳OST ignore 鍚庢秷澶便€丟ET /suggestions/summary 杩斿洖璁℃暟銆傛鏃舵帴鍙ｆ湭瀹炵幇锛屾祴璇曞け璐ワ紙绾級銆?
## Phase 3: 鍚庣瀹炵幇锛圲S1 寤鸿鍒楄〃锛?
- [x] T004 [US1] 鍚庣锛氭柊澧?`service/SuggestionService.java`鈥斺€旇仛鍚堝洓绫诲缓璁紙娴佸け=CustomerService.atRiskCustomers銆佸仠婊?鍋滄粸鍟嗘満鏌ヨ銆佸緟璺熻繘=FollowUpMapper銆侀珮鍒嗙嚎绱?LeadMapper+璇勫垎鈮?0锛夛紝鍘婚噸锛堝悓 entityId 鍙栨渶楂樹紭鍏堢骇锛夈€佹寜浼樺厛绾?鍐呴儴鎺掑簭銆佸拷鐣ヨ繃婊わ紙Redis `ai:ignore:<userId>:<type>:<entityId>`锛夈€佷笂闄?20銆傦紙渚濊禆 T001锛?- [x] T005 [US1] 鍚庣锛氭柊澧?`controller/SuggestionController.java`鈥斺€擥ET /suggestions銆丟ET /suggestions/summary锛沗POST /suggestions/{type}/{entityId}/ignore` 鍐?Redis锛圱TL 90 澶╋級銆傦紙渚濊禆 T004锛?
## Phase 4: 鍚庣瀹炵幇锛圲S2 蹇界暐 / US3 鎽樿锛?
- [x] T006 [P] [US2] 鍚庣锛氬拷鐣ョ鐐瑰啓 Redis SET + TTL锛涘缓璁敓鎴愭椂鐢?Redis 鍒ゆ柇蹇界暐锛堟壒閲忔壂 key 鍓嶇紑锛夈€傦紙渚濊禆 T005锛?- [x] T007 [P] [US3] 鍚庣锛歚GET /suggestions/summary` 杩斿洖鍥涚被璁℃暟锛堜笉鎴柇锛屼粎璁℃暟锛夈€傦紙渚濊禆 T004锛?
## Phase 5: 鍓嶇

- [x] T008 [P] [US1] 鍓嶇锛歚types/suggestion.ts` + `services/suggestionService.ts`锛坒etchSuggestions/ignoreSuggestion/fetchSummary锛夈€?- [x] T009 [US1] 鍓嶇锛氭柊澧?`pages/assistant/SuggestionCenterPage.tsx`鈥斺€斿缓璁垪琛紙绫诲瀷 Tag + 浼樺厛绾ф爣璇?+ 鍘熷洜 + 璺宠浆閾炬帴 + 蹇界暐鎸夐挳锛夛紱`App.tsx` 娉ㄥ唽璺敱锛堟暟鎹垎鏋愬垎缁勶級銆?- [x] T010 [US3] 鍓嶇锛歚DashboardPage` 棣栭〉鍔?AI 鏅鸿兘寤鸿"鎽樿鍗★紙鍥涚被璁℃暟锛岀偣鍑昏烦杞缓璁〉锛夈€?
## Phase 6: 楠岃瘉涓庢敹灏?
- [x] T011 鍚庣锛歚mvn test` 鍏ㄩ噺閫氳繃锛堟柊澧?SuggestionServiceTest + SmartSuggestionIT锛屼笉褰卞搷鏃㈡湁 224锛夈€?- [x] T012 鍓嶇锛歚pnpm run typecheck` + `lint` + `test` 鍏ㄩ噺閫氳繃銆?- [x] T013 [P] 鎵嬪姩鍐掔儫锛氶椤佃寤鸿鎽樿锛涘缓璁垪琛ㄦ寜浼樺厛绾ф帓搴忥紱蹇界暐鍚庢秷澶便€?
## Dependencies & Execution Order

- T001锛堝熀纭€璁炬柦锛夈€?- T002/T003 鍙苟琛岋紝鍧囦负绾㈤樁娈碉紱渚濊禆 T001銆?- T004 渚濊禆 T001锛汿005 渚濊禆 T004銆?- T006 渚濊禆 T005锛汿007 渚濊禆 T004銆?- T008/T009/T010 鍙苟琛岋紙鍓嶇锛夈€?- Phase 6 鍦ㄦ墍鏈夊疄鐜板畬鎴愬悗鎵ц銆?
## Notes

- 瑙勫垯寮曟搸锛堥潪 LLM锛夛紝澶嶇敤 018 娴佸け棰勮/鍋ュ悍搴︺€?06 鍋滄粸棰勮銆?19 绾跨储璇勫垎鏁版嵁銆?- 蹇界暐璁板綍瀛?Redis锛坘ey `ai:ignore:<userId>:<type>:<entityId>`锛孴TL 90 澶╋級銆?- 寤鸿涓婇檺 20 鏉″彲閰嶇疆锛涙暟鎹潈闄?SALES 浠呮湰浜恒€?
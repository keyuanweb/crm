# Tasks: 鍥炴敹绔欎笌鎵归噺鎭㈠

**Input**: Design documents from `/specs/025-recycle-bin/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/recycle-bin.md

**Tests**: 绔犵▼鍘熷垯鍥涜姹傛祴璇曞厛浜庡疄鐜帮紙绾⑩啋缁匡級锛屾湰鍔熻兘鍚悗绔崟鍏?闆嗘垚 + 鍓嶇娓叉煋娴嬭瘯銆?
## Phase 1: 鍩虹璁炬柦

- [x] T001 [P] 鍚庣锛? 涓?Mapper锛圕ustomer/Lead/Contact/Opportunity锛夊姞娉ㄨВ SQL鈥斺€擿selectDeletedByUser`锛園Select 鏌?deleted=1 AND created_by锛夈€乣selectDeletedAll`锛園Select 鏌?deleted=1锛夈€乣restoreById`锛園Update deleted=0锛夈€乣purgeById`锛園Delete 鐗╃悊鍒狅級銆?
## Phase 2: 鍚庣娴嬭瘯鍏堣锛圱DD 绾級

- [x] T002 [P] [US1] 鍚庣锛氱紪鍐?`backend/src/test/java/com/crm/service/RecycleBinServiceTest.java` 鍗曞厓娴嬭瘯鈥斺€旇鐩栵細璺ㄥ疄浣撳垪琛ㄥ悎骞躲€佹寜绫诲瀷/鍏抽敭瀛楄繃婊ゃ€佹仮澶嶏紙鍚鎴峰敮涓€鎬у啿绐佽烦杩囷級銆佸交搴曞垹闄ゃ€丼ALES 鏉冮檺杩囨护銆傛鏃?RecycleBinService 鏈疄鐜帮紝娴嬭瘯缂栬瘧澶辫触锛堢孩锛夈€?- [x] T003 [P] [US1] 鍚庣锛氱紪鍐?`backend/src/test/java/com/crm/integration/RecycleBinIT.java` 闆嗘垚娴嬭瘯鈥斺€斿垹闄ゅ鎴封啋GET /recycle-bin 鍙鈫扨OST restore鈫掑鎴峰垪琛ㄩ噸鐜般€傛鏃舵帴鍙ｆ湭瀹炵幇锛屾祴璇曞け璐ワ紙绾級銆?
## Phase 3: 鍚庣瀹炵幇

- [x] T004 [US1] 鍚庣锛氭柊澧?`dto/recycle/RecycleItem.java`锛坱ype/id/name/deletedAt/deletedBy锛夈€?- [x] T005 [US1] 鍚庣锛氭柊澧?`service/RecycleBinService.java`鈥斺€斿垪琛紙4 瀹炰綋鍚堝苟 + 绫诲瀷/鍏抽敭瀛楄繃婊?+ 鏁版嵁鏉冮檺 + 鍒嗛〉锛夈€佹壒閲忔仮澶嶏紙瀹㈡埛 name+company 鍐茬獊鏍￠獙璺宠繃锛屽叾浣欐仮澶嶏紝瀹¤锛夈€佸交搴曞垹闄わ紙瀹¤锛夈€傦紙渚濊禆 T001/T004锛?- [x] T006 [US1] 鍚庣锛氭柊澧?`controller/RecycleBinController.java`鈥斺€擥ET /recycle-bin銆丳OST /recycle-bin/restore銆丳OST /recycle-bin/purge锛園PreAuthorize ADMIN锛夈€傦紙渚濊禆 T005锛?
## Phase 4: 鍓嶇

- [x] T007 [P] [US1] 鍓嶇锛歚types/recycle.ts` + `services/recycleService.ts`锛坒etchRecycleBin/restoreItems/purgeItems锛夈€?- [x] T008 [US1] 鍓嶇锛氭柊澧?`pages/recycle/RecycleBinPage.tsx`鈥斺€擳able锛堢被鍨?Tag/鍚嶇О/鍒犻櫎鏃堕棿/鍒犻櫎浜猴級+ 绫诲瀷绛涢€?+ 鎼滅储 + 琛岄€夋嫨 + "鎭㈠/褰诲簳鍒犻櫎"鎸夐挳锛圡odal 纭锛夛紱`App.tsx` 娉ㄥ唽璺敱锛堢郴缁熺鐞嗗垎缁勶級銆傦紙渚濊禆 T007锛?
## Phase 5: 楠岃瘉涓庢敹灏?
- [x] T009 鍚庣锛歚mvn test` 鍏ㄩ噺閫氳繃锛堟柊澧?RecycleBinServiceTest + RecycleBinIT锛屼笉褰卞搷鏃㈡湁 233锛夈€?- [x] T010 鍓嶇锛歚pnpm run typecheck` + `lint` + `test` 鍏ㄩ噺閫氳繃銆?- [x] T011 [P] 鎵嬪姩鍐掔儫锛氬垹闄ゅ鎴?鈫?鍥炴敹绔欏彲瑙?鈫?鎭㈠ 鈫?瀹㈡埛鍒楄〃閲嶇幇銆?
## Dependencies & Execution Order

- T001锛堝熀纭€璁炬柦锛夈€?- T002/T003 鍙苟琛岋紝鍧囦负绾㈤樁娈碉紱渚濊禆 T001銆?- T004 渚濊禆鏃狅紱T005 渚濊禆 T001/T004锛汿006 渚濊禆 T005銆?- T007/T008 鍙苟琛岋紙鍓嶇锛夈€?- Phase 5 鍦ㄦ墍鏈夊疄鐜板畬鎴愬悗鎵ц銆?
## Notes

- 鐢ㄦ敞瑙?SQL 缁曡繃 @TableLogic 閫昏緫鍒犻櫎杩囨护銆?- 鎭㈠ = deleted=0锛涘交搴曞垹闄?= 鐗╃悊鍒犮€?- 鍥炴敹绔欎粎 ADMIN锛堢郴缁熺鐞嗗垎缁勶級锛汼ALES 鎵╁睍涓轰粎鏈汉锛堥鐣欙級銆?- 瀹㈡埛鎭㈠鍞竴鎬у啿绐侊紙name+company锛夎烦杩囧苟鎻愮ず銆?
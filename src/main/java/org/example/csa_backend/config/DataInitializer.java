package org.example.csa_backend.config;

import lombok.RequiredArgsConstructor;
import org.example.csa_backend.fairytale.*;
import org.example.csa_backend.user.User;
import org.example.csa_backend.user.UserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CategoryRepository categoryRepository;
    private final FairytaleRepository fairytaleRepository;
    private final FairytaleDetailRepository fairytaleDetailRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!userRepository.existsByEmail("test@test.com")) {
            userRepository.save(new User("test@test.com", passwordEncoder.encode("test1234")));
        }
        initFairytaleData();
        initFairytaleDetailDataIfMissing();
    }

    private void initFairytaleData() {
        if (categoryRepository.count() > 0) return;

        Map<String, Category> cats = Map.of(
            "sea",        categoryRepository.save(new Category("sea",      "바다",    "海")),
            "city",       categoryRepository.save(new Category("city",     "도시·마을", "まち")),
            "forest",     categoryRepository.save(new Category("forest",   "숲·자연", "森・自然")),
            "animal",     categoryRepository.save(new Category("animal",   "동물",    "どうぶつ")),
            "adventure",  categoryRepository.save(new Category("adventure","모험",    "冒険")),
            "fantasy",    categoryRepository.save(new Category("fantasy",  "판타지",  "ファンタジー")),
            "family",     categoryRepository.save(new Category("family",   "가족",    "家族")),
            "kingdom",    categoryRepository.save(new Category("kingdom",  "왕국·성", "王国・お城"))
        );

        // ── 테마 동화 (IS_THEME = Y) ──────────────────────────────────────────
        Fairytale t1 = fairytaleRepository.save(new Fairytale(
            "여름은 인디머쉬", "夏のインディムシュ",
            "해변가를 배경으로 한 여름 동화", "海辺を舞台にした夏のおとぎ話",
            null, "#7EC8C8", "#해변가", "Y", "N", "N"
        ));
        t1.addCategory(cats.get("sea")); t1.addCategory(cats.get("adventure")); t1.addCategory(cats.get("fantasy"));

        Fairytale t2 = fairytaleRepository.save(new Fairytale(
            "애니메이션도", "アニメーション通り",
            "문학거리의 이야기", "文学通りのおはなし",
            null, "#E8A87C", "#문학거리", "Y", "N", "N"
        ));
        t2.addCategory(cats.get("city")); t2.addCategory(cats.get("family"));

        Fairytale t3 = fairytaleRepository.save(new Fairytale(
            "달빛 왕국", "月光の王国",
            "달빛이 빛나는 신비한 왕국의 이야기", "月の光に輝く神秘な王国のおはなし",
            null, "#C9B1FF", "#달빛성", "Y", "N", "N"
        ));
        t3.addCategory(cats.get("kingdom")); t3.addCategory(cats.get("fantasy")); t3.addCategory(cats.get("adventure"));

        Fairytale t4 = fairytaleRepository.save(new Fairytale(
            "숲속 요정", "森の妖精",
            "숲속에 사는 작은 요정의 하루", "森に住む小さな妖精の一日",
            null, "#98D8C8", "#요정의숲", "Y", "N", "N"
        ));
        t4.addCategory(cats.get("forest")); t4.addCategory(cats.get("fantasy")); t4.addCategory(cats.get("animal"));

        Fairytale t5 = fairytaleRepository.save(new Fairytale(
            "신비한 바다", "神秘の海",
            "심해에 숨겨진 비밀 왕국을 찾아서", "深海に隠された秘密の王国を探して",
            null, "#5DADE2", "#심해왕국", "Y", "N", "N"
        ));
        t5.addCategory(cats.get("sea")); t5.addCategory(cats.get("fantasy")); t5.addCategory(cats.get("adventure"));

        // ── 새로운 동화 (IS_NEW = Y) ──────────────────────────────────────────
        Fairytale n1 = fairytaleRepository.save(new Fairytale(
            "가마솥", "魔法の釜",
            "신기한 가마솥이 소원을 들어주는 이야기", "不思議な釜が願いをかなえてくれるおはなし",
            5.0, "#FFD6A5", null, "N", "Y", "N"
        ));
        n1.addCategory(cats.get("fantasy")); n1.addCategory(cats.get("kingdom")); n1.addCategory(cats.get("adventure"));

        Fairytale n2 = fairytaleRepository.save(new Fairytale(
            "나무바닥", "木の床",
            "숲속 작은 집에서 벌어지는 이야기", "森の小さな家で起きるおはなし",
            3.0, "#A8D8EA", null, "N", "Y", "N"
        ));
        n2.addCategory(cats.get("forest")); n2.addCategory(cats.get("animal"));

        Fairytale n3 = fairytaleRepository.save(new Fairytale(
            "울면서 도망가는 아기 곰", "泣きながら逃げる子熊",
            "용감한 아기 곰의 모험", "勇敢な子熊の大冒険",
            5.0, "#FFB7B2", null, "N", "Y", "N"
        ));
        n3.addCategory(cats.get("animal")); n3.addCategory(cats.get("forest")); n3.addCategory(cats.get("adventure"));

        Fairytale n4 = fairytaleRepository.save(new Fairytale(
            "놀란 아기도마뱀", "びっくりトカゲの赤ちゃん",
            "작은 도마뱀의 첫 번째 여행", "小さなトカゲのはじめての旅",
            5.0, "#B5EAD7", null, "N", "Y", "N"
        ));
        n4.addCategory(cats.get("animal")); n4.addCategory(cats.get("adventure")); n4.addCategory(cats.get("sea"));

        Fairytale n5 = fairytaleRepository.save(new Fairytale(
            "호랑이의 눈물", "虎の涙",
            "용감한 호랑이와 작은 새의 우정 이야기", "勇敢なトラと小さな鳥の友情のおはなし",
            4.5, "#FFAAA5", null, "N", "Y", "N"
        ));
        n5.addCategory(cats.get("animal")); n5.addCategory(cats.get("forest")); n5.addCategory(cats.get("family"));

        Fairytale n6 = fairytaleRepository.save(new Fairytale(
            "마법의 씨앗", "魔法の種",
            "작은 씨앗이 세상을 구하는 이야기", "小さな種が世界を救うおはなし",
            4.0, "#D4B0FF", null, "N", "Y", "N"
        ));
        n6.addCategory(cats.get("fantasy")); n6.addCategory(cats.get("forest")); n6.addCategory(cats.get("adventure"));

        Fairytale n7 = fairytaleRepository.save(new Fairytale(
            "별빛 소원", "星明かりの願い",
            "별을 향해 소원을 비는 아이의 이야기", "星に願いをかける子どものおはなし",
            5.0, "#B0C4FF", null, "N", "Y", "N"
        ));
        n7.addCategory(cats.get("fantasy")); n7.addCategory(cats.get("adventure"));

        Fairytale n8 = fairytaleRepository.save(new Fairytale(
            "꿈꾸는 고양이", "夢見る猫",
            "꿈속 세계를 탐험하는 고양이 이야기", "夢の世界を旅する猫のおはなし",
            4.5, "#FFC1E3", null, "N", "Y", "N"
        ));
        n8.addCategory(cats.get("animal")); n8.addCategory(cats.get("city")); n8.addCategory(cats.get("family"));

        // ── 추천 동화 (IS_RECOMMENDED = Y) ───────────────────────────────────
        Fairytale r1 = fairytaleRepository.save(new Fairytale(
            "빨간 사과", "赤いりんご",
            "사과나무 숲의 비밀 이야기", "りんごの木の森の秘密のおはなし",
            null, "#FFB7B2", null, "N", "N", "Y"
        ));
        r1.addCategory(cats.get("forest")); r1.addCategory(cats.get("fantasy")); r1.addCategory(cats.get("family"));

        Fairytale r2 = fairytaleRepository.save(new Fairytale(
            "빵 만들기", "パン作り",
            "마을 빵집에서 벌어지는 따뜻한 이야기", "町のパン屋さんで起きるあたたかいおはなし",
            null, "#FFD6A5", null, "N", "N", "Y"
        ));
        r2.addCategory(cats.get("city")); r2.addCategory(cats.get("family")); r2.addCategory(cats.get("animal"));

        Fairytale r3 = fairytaleRepository.save(new Fairytale(
            "핫도그 파티", "ホットドッグパーティー",
            "친구들과 함께하는 신나는 파티 이야기", "お友だちとたのしいパーティーのおはなし",
            null, "#A8D8EA", null, "N", "N", "Y"
        ));
        r3.addCategory(cats.get("city")); r3.addCategory(cats.get("adventure")); r3.addCategory(cats.get("family")); r3.addCategory(cats.get("fantasy"));

        Fairytale r4 = fairytaleRepository.save(new Fairytale(
            "딸기밭 이야기", "いちご畑のおはなし",
            "딸기밭에서 만난 친구들의 이야기", "いちご畑で出会ったお友だちのおはなし",
            null, "#FF9AA2", null, "N", "N", "Y"
        ));
        r4.addCategory(cats.get("forest")); r4.addCategory(cats.get("family")); r4.addCategory(cats.get("animal"));

        Fairytale r5 = fairytaleRepository.save(new Fairytale(
            "바람 친구들", "風のともだち",
            "바람이 데려다준 새로운 친구들의 이야기", "風が連れてきた新しい仲間たちのおはなし",
            null, "#C7CEEA", null, "N", "N", "Y"
        ));
        r5.addCategory(cats.get("adventure")); r5.addCategory(cats.get("fantasy")); r5.addCategory(cats.get("forest"));

        Fairytale r6 = fairytaleRepository.save(new Fairytale(
            "작은 왕자의 하루", "小さな王子の一日",
            "왕국을 다스리는 작은 왕자의 하루 이야기", "王国を治める小さな王子の一日のおはなし",
            null, "#FFDAC1", null, "N", "N", "Y"
        ));
        r6.addCategory(cats.get("kingdom")); r6.addCategory(cats.get("family")); r6.addCategory(cats.get("adventure"));

        fairytaleRepository.saveAll(List.of(t1, t2, t3, t4, t5, n1, n2, n3, n4, n5, n6, n7, n8, r1, r2, r3, r4, r5, r6));
    }

    private void initFairytaleDetailDataIfMissing() {
        if (fairytaleDetailRepository.count() > 0) return;

        Map<String, Fairytale> byTitle = fairytaleRepository.findAll().stream()
                .collect(Collectors.toMap(Fairytale::getTitle, f -> f, (a, b) -> a));

        String authorKo = "CSA 작가팀";
        String authorJa = "CSA 作家チーム";

        saveDetail(byTitle, "여름은 인디머쉬", authorKo, authorJa, "5-7세", 8, 20,
            "무더운 여름날, 인디머쉬는 반짝이는 해변에서 신기한 조개를 발견했어요. 조개를 살며시 열자, 그 안에서 작은 인어 친구가 나타났어요. 인어와 함께 파도를 타고 물고기들과 어울리며 인디머쉬는 잊지 못할 여름을 보냈답니다.",
            "暑い夏の日、インディムシュは輝く海辺で不思議な貝を見つけました。貝をそっと開けると、中から小さな人魚の友だちが現れました。人魚と一緒に波に乗り魚たちと遊んで、インディムシュは忘れられない夏を過ごしました。");

        saveDetail(byTitle, "애니메이션도", authorKo, authorJa, "5-7세", 7, 18,
            "그림책 속 캐릭터들이 모여 사는 '애니메이션도'라는 골목이 있었어요. 저녁이 되면 캐릭터들이 골목에 나와 즐겁게 이야기꽃을 피웠죠. 우연히 이 골목을 발견한 소녀는 캐릭터들과 함께 따뜻한 우정을 나누게 되었어요.",
            "絵本のキャラクターたちが集まる「アニメーション通り」という路地がありました。夜になるとキャラクターたちが路地に出て楽しくおしゃべりをしていました。偶然この路地を見つけた女の子は、キャラクターたちと温かい友情を結ぶことになりました。");

        saveDetail(byTitle, "달빛 왕국", authorKo, authorJa, "7-10세", 12, 28,
            "달빛이 가득 내리쬐는 밤이면 깨어나는 신비한 왕국이 있었어요. 왕국의 왕자는 달빛 마법으로 슬픈 아이들의 꿈을 아름답게 꾸며 주었죠. 어느 날 왕국으로 찾아온 한 소녀가 왕자와 함께 달빛 여행을 떠났어요.",
            "月の光が満ちる夜に目覚める不思議な王国がありました。王国の王子は月の魔法で悲しい子どもたちの夢を美しく飾ってあげました。ある日王国を訪れた一人の女の子が、王子と一緒に月明かりの旅へ出発しました。");

        saveDetail(byTitle, "숲속 요정", authorKo, authorJa, "3-5세", 6, 14,
            "초록빛 숲 깊은 곳에 작고 귀여운 요정 루미가 살았어요. 루미는 매일 아침 꽃에 이슬을 달아 주고 길 잃은 동물들을 집으로 안내했어요. 어느 날 숲에서 만난 상처 입은 새끼 사슴을 돌보며 루미는 진정한 용기를 배웠답니다.",
            "緑の森の奥深くに小さくてかわいい妖精のルミが住んでいました。ルミは毎朝花に露をつけて、迷子の動物たちを家まで案内してあげました。ある日森で出会ったけがをした子鹿を看病しながら、ルミは本当の勇気を学びました。");

        saveDetail(byTitle, "신비한 바다", authorKo, authorJa, "7-10세", 10, 24,
            "깊은 바다 속 어딘가에 빛나는 왕국이 숨어 있다는 전설이 있었어요. 호기심 많은 소년 마린은 작은 배를 타고 그 왕국을 찾아 나섰어요. 수많은 위험을 헤치고 드디어 왕국에 도착한 마린은 바다의 진짜 비밀을 알게 되었답니다.",
            "深い海のどこかに輝く王国が隠れているという伝説がありました。好奇心旺盛な少年マリンは小さな船に乗ってその王国を探しに出かけました。たくさんの危険を乗り越えてついに王国にたどり着いたマリンは、海の本当の秘密を知ることになりました。");

        saveDetail(byTitle, "가마솥", authorKo, authorJa, "5-7세", 9, 22,
            "마을 광장 한가운데에 오래된 마법 가마솥이 있었어요. 솔직한 마음으로 소원을 빌면 그 소원이 이루어진다고 전해 내려왔죠. 욕심쟁이 대장장이와 착한 소녀가 가마솥 앞에서 서로 다른 소원을 빌었고, 그 결과는 아무도 예상하지 못했어요.",
            "村の広場の真ん中に古い魔法の釜がありました。正直な心で願いを込めると、その願いが叶うと伝えられていました。欲張りな鍛冶屋と心優しい少女が釜の前でそれぞれ違う願いを込め、その結果は誰も予想していませんでした。");

        saveDetail(byTitle, "나무바닥", authorKo, authorJa, "3-5세", 6, 16,
            "숲 가장자리에 낡은 나무 바닥으로 된 작은 집이 있었어요. 그 집 바닥 아래에는 조그만 생쥐 가족이 살고 있었답니다. 어느 날 집 주인이 드디어 그 비밀을 알게 되었고, 둘은 서로를 도우며 살아가게 되었어요.",
            "森のはずれに古い木の床でできた小さな家がありました。その家の床の下には小さなねずみの家族が住んでいたのです。ある日、家の主がついにその秘密を知り、二人はお互いを助け合って暮らすことになりました。");

        saveDetail(byTitle, "울면서 도망가는 아기 곰", authorKo, authorJa, "3-5세", 7, 18,
            "아기 곰 버블은 꿀을 빼앗기고 울면서 숲을 뛰어다녔어요. 친구들에게 도움을 요청하는 게 부끄러워서 혼자 해결하려 했죠. 하지만 숲속 친구들이 먼저 손을 내밀었고, 버블은 용기를 내어 도움을 받아들였어요.",
            "子熊のバブルはハチミツを取られて泣きながら森を走り回りました。友だちに助けを求めるのが恥ずかしくて一人で解決しようとしました。でも森の友だちが先に手を差し伸べて、バブルは勇気を出して助けを受け入れることにしました。");

        saveDetail(byTitle, "놀란 아기도마뱀", authorKo, authorJa, "3-5세", 8, 20,
            "작은 도마뱀 리잘은 태어나서 처음으로 집을 떠나 여행을 시작했어요. 모든 것이 낯설고 무서웠지만, 가는 곳마다 새로운 친구들이 나타났어요. 바다에 닿았을 때 리잘은 세상이 생각보다 훨씬 아름답다는 것을 깨달았어요.",
            "小さなトカゲのリザルは生まれて初めて家を出て旅に出ました。全てが見知らぬもので怖かったけれど、行く先々に新しい友だちが現れました。海にたどり着いたとき、リザルは世界が思っていたよりずっと美しいことに気づきました。");

        saveDetail(byTitle, "호랑이의 눈물", authorKo, authorJa, "5-7세", 10, 24,
            "용감한 호랑이 왕은 언제나 혼자였어요. 눈물을 흘리는 건 나약한 것이라고 믿었거든요. 그러던 어느 날 다친 작은 새를 만나 정성껏 돌봐 주면서, 호랑이는 눈물이 사랑과 강함의 표현임을 비로소 알게 되었어요.",
            "勇敢なトラの王はいつも一人でした。涙を流すことは弱さだと信じていたからです。ある日けがをした小さな鳥に出会って丁寧に看病するうちに、トラは涙が愛と強さの表れだということをようやく知りました。");

        saveDetail(byTitle, "마법의 씨앗", authorKo, authorJa, "5-7세", 9, 22,
            "세상이 점점 메말라 가는 어느 날, 한 소녀가 할머니에게 마법의 씨앗을 선물받았어요. 씨앗을 심기 위해 온 마을을 돌아다니던 소녀는 많은 사람들과 힘을 합쳐 황폐한 숲을 되살렸어요.",
            "世界がだんだん乾いていくある日、一人の女の子がおばあちゃんから魔法の種をもらいました。種を植えるために村中を歩き回った女の子は多くの人々と力を合わせて荒れた森を生き返らせました。");

        saveDetail(byTitle, "별빛 소원", authorKo, authorJa, "5-7세", 8, 20,
            "소원을 빌면 이루어진다는 가장 밝은 별을 찾아 소년은 밤마다 하늘을 올려다보았어요. 드디어 그 별을 발견했지만, 진짜 소원은 별이 아니라 곁에 있는 가족과 친구들이라는 걸 깨달았죠.",
            "願えば叶うという一番明るい星を探して、少年は夜ごとに空を見上げていました。ついにその星を見つけたとき、本当の願いは星ではなく側にいる家族と友だちだということに気づきました。");

        saveDetail(byTitle, "꿈꾸는 고양이", authorKo, authorJa, "5-7세", 9, 22,
            "고양이 루나는 잠들 때마다 다른 세계로 모험을 떠났어요. 어느 날 꿈속에서 자신이 사는 도시가 위험에 처했다는 것을 알게 되었어요. 루나는 꿈과 현실을 오가며 도시를 구하기 위한 단서를 모았어요.",
            "猫のルナは眠るたびに別の世界へ冒険に出かけていました。ある日夢の中で自分が住む街が危険にさらされていることを知りました。ルナは夢と現実を行き来して街を救うための手がかりを集めていきました。");

        saveDetail(byTitle, "빨간 사과", authorKo, authorJa, "3-5세", 6, 14,
            "깊은 숲 속에 반짝이는 빨간 사과가 열리는 나무 한 그루가 있었어요. 사과를 먹으면 소원이 이루어진다는 소문을 듣고 많은 이들이 찾아왔죠. 나무는 오직 진심으로 나누려는 마음을 가진 사람에게만 사과를 내어 주었어요.",
            "深い森の中に輝く赤いりんごが実る木が一本ありました。りんごを食べると願いが叶うという噂を聞いて大勢の人が訪れました。でも木は本当に分かち合う心を持つ人にだけりんごを与えてくれたのです。");

        saveDetail(byTitle, "빵 만들기", authorKo, authorJa, "3-5세", 7, 16,
            "작은 마을의 빵집 할아버지는 매일 새벽마다 온 마을을 위한 빵을 구웠어요. 어느 날 밀가루가 다 떨어지자 마을 사람들이 하나둘씩 재료를 가져왔어요. 함께 빵을 만들면서 마을 사람들은 이웃의 소중함을 다시 한번 느꼈답니다.",
            "小さな村のパン屋のおじいさんは毎朝早くから村全体のためにパンを焼いていました。ある日小麦粉が切れると村人たちが一人一人材料を持ってきました。一緒にパンを作りながら村人たちは隣人の大切さを改めて感じました。");

        saveDetail(byTitle, "핫도그 파티", authorKo, authorJa, "3-5세", 6, 14,
            "마을 광장에서 핫도그 파티가 열린 날, 모두가 각자만의 특별한 재료를 가져왔어요. 처음에는 서로 다른 맛이 어울릴지 걱정했지만, 함께 먹어 보니 세상에서 가장 맛있는 핫도그가 완성되었어요.",
            "村の広場でホットドッグパーティーが開かれた日、みんなそれぞれ特別な材料を持ってきました。最初は違う味が合うか心配でしたが、一緒に食べてみると世界で一番おいしいホットドッグができあがりました。");

        saveDetail(byTitle, "딸기밭 이야기", authorKo, authorJa, "3-5세", 6, 14,
            "봄이 되면 마을 끝에 있는 딸기밭이 빨갛게 물들었어요. 딸기밭 주인 할머니는 누구에게든 딸기를 나눠 주었고, 그 덕분에 마을엔 항상 웃음이 넘쳤답니다. 할머니의 딸기밭에는 나눔의 마법이 깃들어 있었어요.",
            "春になると村はずれのいちご畑が真っ赤に染まりました。いちご畑の持ち主のおばあさんは誰にでもいちごを分けてあげて、そのおかげで村にはいつも笑顔があふれていました。おばあさんのいちご畑には分かち合いの魔法が宿っていたのです。");

        saveDetail(byTitle, "바람 친구들", authorKo, authorJa, "5-7세", 8, 18,
            "산들바람을 타고 다니는 작은 정령들이 있었어요. 정령들은 외로운 사람들이 있는 곳으로 날아가 장난스럽게 친구를 연결해 주었어요. 혼자라고 느끼던 소년도 바람 정령 덕분에 평생의 친구를 만나게 되었죠.",
            "そよ風に乗って旅する小さな精霊たちがいました。精霊たちは孤独な人のところへ飛んでいっていたずらっぽく友だちをつないであげました。一人だと感じていた少年も風の精霊のおかげで一生の友だちに出会えました。");

        saveDetail(byTitle, "작은 왕자의 하루", authorKo, authorJa, "5-7세", 10, 26,
            "조그마한 왕국을 다스리는 어린 왕자는 신하들이 하는 일이 궁금했어요. 그래서 하루 동안 변장을 하고 백성들 사이에서 살아 보기로 했죠. 평범한 하루를 보내면서 왕자는 작은 친절이 세상을 얼마나 따뜻하게 만드는지 배웠어요.",
            "小さな王国を治める幼い王子は家臣たちの仕事が気になりました。それで一日変装して民衆の中で生活してみることにしました。普通の一日を過ごしながら王子は小さな優しさがどれほど世界を温かくするかを学びました。");
    }

    private void saveDetail(Map<String, Fairytale> byTitle, String title,
                            String authorKo, String authorJa, String ageRange,
                            int durationMin, int pageCount,
                            String fullContentKo, String fullContentJa) {
        Fairytale fairytale = byTitle.get(title);
        if (fairytale == null) return;
        fairytaleDetailRepository.save(new FairytaleDetail(
                fairytale, authorKo, authorJa, ageRange, durationMin, pageCount, fullContentKo, fullContentJa));
    }
}

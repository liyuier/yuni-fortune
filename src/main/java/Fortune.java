import com.yuier.yuni.core.api.message.SendMessage;
import com.yuier.yuni.core.model.message.MessageChain;
import com.yuier.yuni.core.model.message.segment.ImageSegment;
import com.yuier.yuni.core.util.RedisUtil;
import com.yuier.yuni.event.context.YuniMessageEvent;
import com.yuier.yuni.event.detector.message.command.CommandDetector;
import com.yuier.yuni.event.detector.message.command.model.CommandBuilder;
import com.yuier.yuni.plugin.model.passive.message.CommandPlugin;
import com.yuier.yuni.plugin.util.PluginUtils;
import entity.FortuneItem;
import entity.FortuneTodayCache;
import entity.PrincessFortune;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.*;
import java.util.List;

/**
 * @Title: Fortune
 * @Author yuier
 * @Package PACKAGE_NAME
 * @Date 2026/2/8 12:59
 * @description: 每日运势
 */

@Slf4j
public class Fortune extends CommandPlugin {

    private static final String FORTUNE_TODAY_CACHE_KEY = "plugin:fortune:key";

    @Override
    public CommandDetector getDetector() {
        return new CommandDetector(CommandBuilder.create("运势").build());
    }

    @Override
    public void execute(YuniMessageEvent eventContext) {
        Long userId = eventContext.getUserId();
        // 先去缓存里看一看有没有
        if (RedisUtil.exists(FORTUNE_TODAY_CACHE_KEY)) {
            Map<String, String> fortuneTodayMap = (Map<String, String>) RedisUtil.get(FORTUNE_TODAY_CACHE_KEY);
            if (fortuneTodayMap != null && fortuneTodayMap.containsKey(userId.toString())) {
                FortuneTodayCache fortuneTodayCache = PluginUtils.deserialize(fortuneTodayMap.get(userId.toString()), FortuneTodayCache.class);
                assert fortuneTodayCache != null;
                if (LocalDate.now().toString().equals(fortuneTodayCache.getDate())) {
                    eventContext.getChatSession().reply(new MessageChain("你今天已经抽取过运势，结果如下: \n").addAll(buildFortuneMessage(fortuneTodayCache)));
                }
                return;
            }
        } else {
            // 如果缓存里没有 Map 的键，先建立一下
            RedisUtil.set(FORTUNE_TODAY_CACHE_KEY, new HashMap<String, String>());
        }
        // 缓存里没有，或者已经过期，本地取一下
        List<PrincessFortune> princessFortuneList = Arrays.asList(PluginUtils.loadJsonConfigFromPlugin("special/pcr.json", PrincessFortune[].class, this.getClass()));
        PrincessFortune princessFortune = PluginUtils.getRandomElement(princessFortuneList);
        // 背景图
        String background = PluginUtils.getRandomElement(princessFortune.getBackgrounds());
        // 具体的签项
        FortuneItem fortuneItem = PluginUtils.getRandomElement(princessFortune.getFortunePool());
        FortuneTodayCache fortuneTodayCache = new FortuneTodayCache(
                fortuneItem.getTitle(),
                fortuneItem.getComment(),
                background,
                LocalDate.now().toString()
        );
        SendMessage sendMessage = eventContext.getChatSession().reply(new MessageChain("你今天抽取的运势是: \n").addAll(buildFortuneMessage(fortuneTodayCache)));
        // 如果取到的签是凶，那么换成姬吉
        if (fortuneItem.getTitle().contains("凶")) {
            List<FortuneItem> hiMeList = findHiMe(princessFortune);
            if (hiMeList.isEmpty()) {
                return;
            }
            FortuneItem randomElement = PluginUtils.getRandomElement(hiMeList);
            fortuneTodayCache = new FortuneTodayCache(
                    randomElement.getTitle(),
                    randomElement.getComment(),
                    background,
                    fortuneTodayCache.getDate()
            );
            eventContext.getChatSession().response(new MessageChain("逢凶化吉！已为你将运势替换为姬吉！\n")
                    .addAll(buildFortuneMessage(fortuneTodayCache))
                    .addReply(String.valueOf(sendMessage.getMessageId())));
            // 缓存起来
            Map<String, String> pigTodayMap = (Map<String, String>) RedisUtil.get(FORTUNE_TODAY_CACHE_KEY);
            pigTodayMap.put(userId.toString(), PluginUtils.serialize(fortuneTodayCache));
            RedisUtil.set(FORTUNE_TODAY_CACHE_KEY, pigTodayMap);
            return;
        }
        // 缓存起来
        Map<String, String> pigTodayMap = (Map<String, String>) RedisUtil.get(FORTUNE_TODAY_CACHE_KEY);
        pigTodayMap.put(userId.toString(), PluginUtils.serialize(fortuneTodayCache));
        RedisUtil.set(FORTUNE_TODAY_CACHE_KEY, pigTodayMap);
    }

    /**
     * 找到姬吉
     * @param fortune 某位公主的签配置
     * @return 姬吉
     */
    List<FortuneItem> findHiMe(PrincessFortune fortune) {
        List<FortuneItem> hiMeList = new ArrayList<>();
        for (FortuneItem fortuneItem : fortune.getFortunePool()) {
            if (fortuneItem.getTitle().equals("姫吉")) {
                hiMeList.add(fortuneItem);
            }
        }
        return hiMeList;
    }

    /**
     * 构建运势消息
     * @param fortuneTodayCache 运势缓存
     * @return 运势消息
     */
    private MessageChain buildFortuneMessage(FortuneTodayCache fortuneTodayCache) {
        /* 获取绘画素材 */
        String title = fortuneTodayCache.getTitle();
        String text = fortuneTodayCache.getComment();
        // 字体
        Integer titleFontSize = 45;
        Integer commentFontSize = 25;
        Font titleFont = PluginUtils.loadFontFromPlugin(this, "font/Mamelon.otf", titleFontSize);
        Font textFont = PluginUtils.loadFontFromPlugin(this, "font/sakura.ttf", commentFontSize);
        Path imgPath = Paths.get(PluginUtils.getPluginRootPath(this.getClass()),  "img", fortuneTodayCache.getBackground());
        // 背景图片
        BufferedImage img = null;
        try {
            img = ImageIO.read(imgPath.toFile());
        } catch (IOException e) {
            log.error("获取图片失败。 imgName: {}", imgPath);
        }

        // 创建Graphics2D对象（对应Python的ImageDraw.Draw）
        Graphics2D g2d = img.createGraphics();
        // 开启抗锯齿，提升文字绘制清晰度
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // ========== 1. 绘制标题（居中） ==========
        Color titleColor = Color.decode("#F5F5F5"); // 标题颜色
        int[] titleCenter = {140, 99}; // 标题中心坐标

        // 获取标题文字的宽高（对应Python的getsize）
        FontRenderContext frc = g2d.getFontRenderContext();
        Rectangle2D titleBounds = titleFont.getStringBounds(title, frc);
        int titleWidth = (int) titleBounds.getWidth();
        int titleHeight = (int) titleBounds.getHeight();

        // 计算标题居中位置
        int titleX = titleCenter[0] - titleWidth / 2;
        int titleY = titleCenter[1] + titleHeight / 2 - 15; // Java的y坐标是文字基线，，这里向上微调 15 像素

        // 设置标题颜色和字体
        g2d.setColor(titleColor);
        g2d.setFont(titleFont);
        // 绘制标题
        g2d.drawString(title, titleX, titleY);

        // ========== 2. 绘制正文（竖排） ==========
        Color textColor = Color.decode("#323232"); // 正文颜色
        int[] textCenter = {140, 297}; // 正文中心坐标
        int textFontSize = textFont.getSize(); // 正文字号

        // 拆分文本为竖排列（复用Python的decrement逻辑）
        TextSplitResult splitResult = decrement(text);
        int slices = splitResult.getColNum();
        List<String> result = splitResult.getTextList();

        // 设置正文颜色和字体
        g2d.setColor(textColor);
        g2d.setFont(textFont);

        // 循环绘制每列竖排文字
        for (int i = 0; i < slices; i++) {
            String colText = result.get(i);
            // 计算当前列文字总高度（每行一个字符，包含4px行间距）
            int fontHeight = colText.length() * (textFontSize + 4);
            // 计算列的水平位置（对应Python的x坐标逻辑）
            int x = (int) (textCenter[0]
                    + (slices - 2) * textFontSize / 2.0
                    + (slices - 1) * 4
                    - i * (textFontSize + 4));
            // 计算列的垂直起始位置（居中），这里向下微调 22 像素
            int yStart = textCenter[1] - fontHeight / 2 + 22;

            // 逐字符绘制竖排文字（Java无直接换行竖排，需手动逐字符绘制）
            for (int j = 0; j < colText.length(); j++) {
                char c = colText.charAt(j);
                // 每个字符的y坐标：起始y + j*(字号+行间距)
                int charY = yStart + j * (textFontSize + 4);
                // 绘制单个字符
                g2d.drawString(String.valueOf(c), x, charY);
            }
        }

        // 释放Graphics2D资源
        g2d.dispose();

        // ========== 3. 生成Base64字符串（不保存到磁盘） ==========
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // 将图片写入字节流（PNG格式，对应Python的BytesIO）
        try {
            ImageIO.write(img, "PNG", baos);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        // 编码为Base64字符串
        byte[] imageBytes = baos.toByteArray();
        String base64Str = Base64.getEncoder().encodeToString(imageBytes);

        // 关闭字节流
        try {
            baos.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return new MessageChain(new ImageSegment().setBase64File(base64Str));
    }

    /**
     * 文本拆分函数（对应Python的decrement）
     * @param text 原始正文文本
     * @return 文本拆分结果（列数+每列文本列表）
     */
    public TextSplitResult decrement(String text) {
        int length = text.length();
        List<String> result = new ArrayList<>();
        int cardinality = 9; // 每列最大字符数

        // 文本过长抛出异常
        if (length > 4 * cardinality) {
            throw new IllegalArgumentException("文本长度超过最大限制（36个字符）");
        }

        // 计算列数
        int colNum = 1;
        int tempLength = length;
        while (tempLength > cardinality) {
            colNum++;
            tempLength -= cardinality;
        }

        // 优化两列布局（核心逻辑和Python一致）
        String space = " ";
        if (colNum == 2) {
            List<String> twoColList = new ArrayList<>();
            if (length % 2 == 0) {
                int fillLen = 9 - length / 2;
                String fillIn = space.repeat(fillLen);
                String firstCol = text.substring(0, length / 2) + fillIn;
                String secondCol = fillIn + text.substring(length / 2);
                twoColList.add(firstCol);
                twoColList.add(secondCol);
                return new TextSplitResult(colNum, twoColList);
            } else {
                int fillLen = 9 - (length + 1) / 2;
                String fillIn = space.repeat(fillLen);
                String firstCol = text.substring(0, (length + 1) / 2) + fillIn;
                String secondCol = fillIn + space + text.substring((length + 1) / 2);
                twoColList.add(firstCol);
                twoColList.add(secondCol);
                return new TextSplitResult(colNum, twoColList);
            }
        }

        // 处理其他列数
        for (int i = 0; i < colNum; i++) {
            int start = i * cardinality;
            int end = (i == colNum - 1) ? text.length() : (i + 1) * cardinality;
            result.add(text.substring(start, end));
        }

        return new TextSplitResult(colNum, result);
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public class TextSplitResult {
        // 列数
        private int colNum;
        // 每列的文本列表
        private List<String> textList;
    }
}

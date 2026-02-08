package entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Title: FortuneTodayCache
 * @Author yuier
 * @Package entity
 * @Date 2026/2/8 13:09
 * @description: 今日运势缓存对象
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FortuneTodayCache {

    // 运势标题
    private String title;
    // 运势内容
    private String comment;
    // 运势图片名称
    private String background;
    // 运势时间
    private String date;
}

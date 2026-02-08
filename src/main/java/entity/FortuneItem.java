package entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Title: FortuneItem
 * @Author yuier
 * @Package entity
 * @Date 2026/2/8 15:46
 * @description: 单个签项
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FortuneItem {
    private String title;
    private String comment;
}

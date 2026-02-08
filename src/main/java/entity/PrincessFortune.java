package entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @Title: PrincessFortune
 * @Author yuier
 * @Package entity
 * @Date 2026/2/8 15:44
 * @description: 公主连结主题抽签
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PrincessFortune {

    private String name;
    private List<String> backgrounds;
    private List<FortuneItem> fortunePool;
}

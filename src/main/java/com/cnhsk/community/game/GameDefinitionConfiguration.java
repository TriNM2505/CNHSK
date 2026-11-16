package com.cnhsk.community.game;

import com.cnhsk.community.game.brush.BrushStrokeGame;
import com.cnhsk.community.game.catchgame.CharCatchGame;
import com.cnhsk.community.game.dart.FlyingDartGame;
import com.cnhsk.community.game.pinyin.PinyinMatchGame;
import com.cnhsk.community.game.radical.RadicalMatchGame;
import com.cnhsk.community.game.rain.CharRainGame;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class GameDefinitionConfiguration {
    @Bean CharRainGame charRainGame() { return new CharRainGame(); }
    @Bean PinyinMatchGame pinyinMatchGame() { return new PinyinMatchGame(); }
    @Bean RadicalMatchGame radicalMatchGame() { return new RadicalMatchGame(); }
    @Bean CharCatchGame charCatchGame() { return new CharCatchGame(); }
    @Bean BrushStrokeGame brushStrokeGame() { return new BrushStrokeGame(); }
    @Bean FlyingDartGame flyingDartGame() { return new FlyingDartGame(); }
}

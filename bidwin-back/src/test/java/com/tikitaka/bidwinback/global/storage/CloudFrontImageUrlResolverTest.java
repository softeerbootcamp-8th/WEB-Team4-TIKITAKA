package com.tikitaka.bidwinback.global.storage;

import com.tikitaka.bidwinback.global.config.CloudFrontProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class CloudFrontImageUrlResolverTest {

    @Test
    void CloudFront_도메인과_objectKey를_조회_URL로_조합한다() {
        CloudFrontImageUrlResolver resolver = new CloudFrontImageUrlResolver(
                new CloudFrontProperties("cdn.example.com/")
        );

        String url = resolver.resolve("auction-images/product.jpg");

        assertThat(url).isEqualTo("https://cdn.example.com/auction-images/product.jpg");
    }

    @Test
    void 절대_URL_형식의_objectKey는_허용하지_않는다() {
        CloudFrontImageUrlResolver resolver = new CloudFrontImageUrlResolver(
                new CloudFrontProperties("https://cdn.example.com")
        );

        assertThatIllegalArgumentException()
                .isThrownBy(() -> resolver.resolve("https://attacker.example/image.jpg"));
    }
}

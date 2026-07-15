package org.zipzip.zipzipserver.domain.photo.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * PHOTO-02 파일 크기 상한(20MiB)이 실제로 서버 인스턴스 자원(이슈 #70 ADR API-14, 6GB ARM 공유 인스턴스)에 영향을 주는지 확인하기 위한 조사용
 * 벤치마크. {@link ThumbnailProcessingService}가 원본을 통째로 다운로드해 {@link ThumbnailImageProcessor}로 압축
 * 해제·리사이즈·재인코딩하는 경로에서, 파일 크기별로 실제 힙 사용량이 어떻게 늘어나는지와 스레드풀 최대 동시성(4~6개, {@code thumbnailExecutor})에서
 * 합산 피크가 얼마나 되는지를 측정한다.
 *
 * <p>정확한 pass/fail 기준이 있는 회귀 테스트가 아니라 수치를 남기기 위한 도구이므로 기본 {@code test} 태스크 실행에서 제외한다({@code
 * build.gradle}의 {@code excludeTags 'benchmark'} 참고). 실행: {@code ./gradlew test --tests
 * "*ThumbnailMemoryBenchmarkTest" -PincludeBenchmark} 또는 IDE에서 개별 실행.
 *
 * <p>힙 측정은 {@code Runtime}의 total/free 메모리 차이를 별도 샘플러 스레드로 폴링하는 방식이라 GC 타이밍에 따라 노이즈가 있다. 절대값보다 "파일
 * 크기가 커질수록 증가폭이 어떻게 변하는가"라는 상대적 경향과, 6GB 공유 인스턴스에서 실제 여유 힙(수 GB 수준)과 비교했을 때 자릿수가 맞는지를 보는 용도로 쓴다.
 */
class ThumbnailMemoryBenchmarkTest {

    private static final int PHOTO_WIDTH = 4032;
    private static final int PHOTO_HEIGHT = 3024;
    private static final int MAX_CONCURRENCY = 6;
    private static final int SAMPLER_INTERVAL_MILLIS = 2;

    private final ThumbnailImageProcessor processor = new ThumbnailImageProcessor();

    @Test
    @Tag("benchmark")
    void 파일_크기별_단일_실행_피크_메모리를_측정한다() throws Exception {
        BufferedImage source = photoLikeImage(PHOTO_WIDTH, PHOTO_HEIGHT);
        float[] qualityLevels = {0.15f, 0.4f, 0.65f, 0.85f, 1.0f};

        System.out.println("\n=== 단일 실행 파일 크기별 피크 메모리 ===");
        for (float quality : qualityLevels) {
            byte[] jpeg = encodeJpeg(source, quality);
            double sizeMiB = jpeg.length / 1024.0 / 1024.0;

            long peakDelta = samplePeakDeltaDuring(() -> processor.createThumbnail(jpeg));

            System.out.printf(
                    "quality=%.2f  파일크기=%.2fMiB  디코딩+리사이즈+인코딩 피크 증가량=%.2fMiB%n",
                    quality, sizeMiB, peakDelta / 1024.0 / 1024.0);

            assertThat(sizeMiB).isGreaterThan(0);
        }
    }

    @Test
    @Tag("benchmark")
    void 최대_동시성으로_실행할_때_합산_피크_메모리를_측정한다() throws Exception {
        BufferedImage source = photoLikeImage(PHOTO_WIDTH, PHOTO_HEIGHT);
        byte[] nearCapFileJpeg = encodeJpeg(source, 1.0f);
        double sizeMiB = nearCapFileJpeg.length / 1024.0 / 1024.0;

        long peakDelta =
                samplePeakDeltaDuring(
                        () -> {
                            ExecutorService executor =
                                    Executors.newFixedThreadPool(MAX_CONCURRENCY);
                            try {
                                List<Future<byte[]>> futures = new ArrayList<>();
                                for (int i = 0; i < MAX_CONCURRENCY; i++) {
                                    futures.add(
                                            executor.submit(
                                                    () ->
                                                            processor.createThumbnail(
                                                                    nearCapFileJpeg)));
                                }
                                for (Future<byte[]> future : futures) {
                                    future.get();
                                }
                            } finally {
                                executor.shutdown();
                            }
                        });

        System.out.println("\n=== thumbnailExecutor 최대 동시성(6) 시뮬레이션 ===");
        System.out.printf(
                "파일크기=%.2fMiB x 동시 %d개 실행 시 합산 피크 증가량=%.2fMiB (JVM 전체 힙 기준)%n",
                sizeMiB, MAX_CONCURRENCY, peakDelta / 1024.0 / 1024.0);

        assertThat(peakDelta).isGreaterThan(0);
    }

    @Test
    @Tag("benchmark")
    void 압축_파일_크기가_비슷해도_해상도가_크면_피크_메모리가_커진다() throws Exception {
        // width/height는 PhotoUploadCompleteRequest에서 검증 없이 그대로 저장되는 클라이언트
        // 제공 메타데이터일 뿐이라, sizeBytes(20MiB) 상한만으로는 실제 디코딩 해상도를 통제하지 못한다.
        BufferedImage smallResolution = photoLikeImage(PHOTO_WIDTH, PHOTO_HEIGHT);
        BufferedImage largeResolution = photoLikeImage(PHOTO_WIDTH * 2, PHOTO_HEIGHT * 2);

        byte[] smallJpeg = encodeJpeg(smallResolution, 0.5f);
        byte[] largeJpeg = encodeJpeg(largeResolution, 0.08f);

        long smallPeak = samplePeakDeltaDuring(() -> processor.createThumbnail(smallJpeg));
        long largePeak = samplePeakDeltaDuring(() -> processor.createThumbnail(largeJpeg));

        System.out.println("\n=== 같은 파일 크기대에서 해상도 차이에 따른 피크 메모리 ===");
        System.out.printf(
                "%dx%d(%.1fMP)  파일크기=%.2fMiB  피크증가량=%.2fMiB%n",
                PHOTO_WIDTH,
                PHOTO_HEIGHT,
                PHOTO_WIDTH * PHOTO_HEIGHT / 1_000_000.0,
                smallJpeg.length / 1024.0 / 1024.0,
                smallPeak / 1024.0 / 1024.0);
        System.out.printf(
                "%dx%d(%.1fMP)  파일크기=%.2fMiB  피크증가량=%.2fMiB%n",
                PHOTO_WIDTH * 2,
                PHOTO_HEIGHT * 2,
                PHOTO_WIDTH * 2L * PHOTO_HEIGHT * 2 / 1_000_000.0,
                largeJpeg.length / 1024.0 / 1024.0,
                largePeak / 1024.0 / 1024.0);

        assertThat(largePeak).isGreaterThan(smallPeak);
    }

    /** 감마 그라디언트 + 노이즈로 실제 사진과 비슷한 압축률을 내는 이미지를 만든다(단색·순수노이즈는 비현실적). */
    private BufferedImage photoLikeImage(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(42);
        int[] row = new int[width];
        for (int y = 0; y < height; y++) {
            double gradient = (double) y / height;
            for (int x = 0; x < width; x++) {
                int base = (int) (128 + 100 * Math.sin(gradient * Math.PI + (double) x / width));
                int noise = random.nextInt(40) - 20;
                int channel = clamp(base + noise);
                row[x] = (channel << 16) | (clamp(channel + random.nextInt(20)) << 8) | channel;
            }
            image.setRGB(0, y, width, 1, row, 0, width);
        }
        return image;
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private byte[] encodeJpeg(BufferedImage image, float quality) throws Exception {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam params = writer.getDefaultWriteParam();
        params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        params.setCompressionQuality(quality);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream imageOutputStream =
                new MemoryCacheImageOutputStream(output)) {
            writer.setOutput(imageOutputStream);
            writer.write(null, new IIOImage(image, null, null), params);
        } finally {
            writer.dispose();
        }
        return output.toByteArray();
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private long samplePeakDeltaDuring(ThrowingRunnable operation) throws Exception {
        long baseline = forceGcAndGetUsedMemory();
        AtomicLong peak = new AtomicLong(baseline);
        AtomicBoolean running = new AtomicBoolean(true);
        Thread sampler =
                new Thread(
                        () -> {
                            Runtime runtime = Runtime.getRuntime();
                            while (running.get()) {
                                long used = runtime.totalMemory() - runtime.freeMemory();
                                peak.updateAndGet(previous -> Math.max(previous, used));
                                try {
                                    Thread.sleep(SAMPLER_INTERVAL_MILLIS);
                                } catch (InterruptedException exception) {
                                    Thread.currentThread().interrupt();
                                    return;
                                }
                            }
                        });
        sampler.start();
        try {
            operation.run();
        } finally {
            running.set(false);
            sampler.join();
        }
        return peak.get() - baseline;
    }

    private long forceGcAndGetUsedMemory() throws InterruptedException {
        Runtime runtime = Runtime.getRuntime();
        for (int i = 0; i < 3; i++) {
            runtime.gc();
        }
        Thread.sleep(50);
        return runtime.totalMemory() - runtime.freeMemory();
    }
}

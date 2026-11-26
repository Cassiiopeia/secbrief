#!/usr/bin/env python3
"""
브랜드 아이콘 원본 1장 > 플랫폼 사본 7개 (앱 아이콘, Android 적응형 3장, 시작 화면, 알림, 웹)
원본 확인 > 배경에서 무늬 분리 > 사본별 크기·배경으로 합성 > 저장 (같은 원본이면 같은 파일)

실행: python3 brand/generate_platform_assets.py  (Python 3 + Pillow)
"""
import json
import math
import sys
from pathlib import Path
from typing import NamedTuple

from PIL import Image, ImageDraw, ImageFilter

BRAND_FOLDER = Path(__file__).resolve().parent
REPOSITORY_ROOT = BRAND_FOLDER.parent
# 아이콘 원본: 단색 배경 위 무늬, 정사각형. 아이콘을 바꿀 때는 이 파일 하나만 바꿈
APP_ICON_SOURCE_PATH = BRAND_FOLDER / "app-icon-source.png"
BRAND_COLORS_PATH = BRAND_FOLDER / "brand-colors.json"
# 사본 위치와 파일 이름: mobile/app.json 이 이 이름으로 읽음
PLATFORM_IMAGES_FOLDER = REPOSITORY_ROOT / "mobile" / "assets" / "images"
APP_ICON_FILE_NAME = "icon.png"
ADAPTIVE_FOREGROUND_FILE_NAME = "android-icon-foreground.png"
ADAPTIVE_BACKGROUND_FILE_NAME = "android-icon-background.png"
ADAPTIVE_MONOCHROME_FILE_NAME = "android-icon-monochrome.png"
SPLASH_ICON_FILE_NAME = "splash-icon.png"
NOTIFICATION_ICON_FILE_NAME = "notification-icon.png"
FAVICON_FILE_NAME = "favicon.png"

LOG_PREFIX = "[brand assets]"
SILHOUETTE_COLOR = (255, 255, 255)

# 앱 아이콘: App Store 규격 1024, 알파 채널 없음
APP_ICON_SIZE_PIXELS = 1024

# 원본 배경 추정: 바깥 테두리 띠 (한 변의 4%) 의 채널별 중앙값
SOURCE_BORDER_RATIO = 0.04
# 배경 잡음으로 보는 채널 값 차이 (이하 투명). 띠 안 값의 폭이 이보다 크면 단색 배경이 아님 (그라데이션 원본은 색 거리로 못 나눔)
SOURCE_BACKGROUND_NOISE_LEVELS = 8
# 배경에서 한쪽 끝(0 또는 255)까지 남은 폭이 이보다 좁으면 그 방향 차이는 무늬로 안 봄 (검정 배경 위 더 어두운 테두리 잡티 방지)
SOURCE_MINIMUM_HEADROOM_LEVELS = 64
# 원본 배경색 <-> appIconBackground 채널 차이 허용치 (넘으면 brand-colors.json 을 먼저 바꿔야 함)
SOURCE_BACKGROUND_MISMATCH_LIMIT = 24
# 무늬 범위로 세는 최소 알파 (0~255)
MOTIF_VISIBLE_ALPHA = 16

# Android 적응형 아이콘: 108dp 캔버스, 가운데 지름 66dp 원이 모든 마스크에서 보이는 안전 영역
ADAPTIVE_ICON_SIZE_PIXELS = 1024
ADAPTIVE_CANVAS_DP = 108
ADAPTIVE_SAFE_ZONE_DP = 66
# 안전 영역 반지름 중 무늬가 쓰는 비율 (원·둥근 사각 마스크에서 iOS 아이콘과 비슷한 여백)
ADAPTIVE_SAFE_ZONE_FILL_RATIO = 0.74
# 실루엣 알파 구간 (0~255): 위 값 이상은 꽉 찬 것으로 봄 (원본 색이 조금 어두워도 구멍 없이)
SILHOUETTE_ALPHA_RANGE = (0, 230)

# 시작 화면·웹: 아이콘을 둥근 타일로 (밝은 배경에서도 무늬가 보이게). 모서리 반지름 = 한 변의 22.37% (iOS 아이콘 비율)
TILE_CORNER_RADIUS_RATIO = 0.2237
TILE_MASK_SUPERSAMPLE = 4
# 시작 화면: 타일이 이미지를 꽉 채움 > app.json 의 imageWidth 가 곧 타일 너비
SPLASH_ICON_SIZE_PIXELS = 1024
FAVICON_SIZE_PIXELS = 48

# Android 알림 아이콘: 24dp 의 4배(xxxhdpi), 내용은 가운데 22dp
NOTIFICATION_ICON_SIZE_PIXELS = 96
NOTIFICATION_ICON_DP = 24
NOTIFICATION_CONTENT_DP = 22
NOTIFICATION_SUPERSAMPLE = 8
# 알림 아이콘 윤곽 정리 2단계 (알파 구간 0~255: 아래 값 이하 투명, 위 값 이상 불투명)
# 1) 옅은 번짐 버리기: 거의 꽉 찬 곳만 남김 (번지는 면이 작은 크기에서 뭉개지지 않게)
NOTIFICATION_FADE_CUT_ALPHA_RANGE = (200, 232)
# 2) 선 굵히기: 흐림 반지름(dp)만큼 번지게 한 뒤 낮은 구간에서 자름 (구간이 낮을수록 굵어짐)
NOTIFICATION_THICKEN_BLUR_DP = 0.5
NOTIFICATION_THICKEN_ALPHA_RANGE = (60, 84)

Color = tuple[int, int, int]


class MotifExtent(NamedTuple):
    """무늬가 차지하는 범위: 중심과 중심에서 가장 먼 점까지의 거리 (원본 픽셀)"""
    center_x: float
    center_y: float
    radius: float


def main() -> int:
    """원본 확인 > 무늬 분리 > 사본 7개 저장"""
    icon_background_color = read_app_icon_background_color()
    source_image = Image.open(APP_ICON_SOURCE_PATH).convert("RGB")
    source_background_color, source_background_spread = measure_source_background(source_image)

    # 원본 검증 (정사각형, 단색 배경, 브랜드 배경색과 같은 계열)
    rejection_reason = find_source_rejection_reason(
        source_image.size, source_background_color, source_background_spread, icon_background_color
    )
    if rejection_reason:
        print(f"{LOG_PREFIX} rejected : {rejection_reason}", file=sys.stderr)
        return 1

    # 무늬 분리 (1024 기준)
    source_image = source_image.resize((APP_ICON_SIZE_PIXELS, APP_ICON_SIZE_PIXELS), Image.Resampling.LANCZOS)
    motif_layer = separate_motif_layer(source_image, source_background_color)
    motif_extent = measure_motif_extent(motif_layer)

    # 사본 합성
    app_icon = build_app_icon(motif_layer, icon_background_color)
    adaptive_foreground = build_adaptive_foreground(motif_layer, motif_extent)
    adaptive_size = (ADAPTIVE_ICON_SIZE_PIXELS, ADAPTIVE_ICON_SIZE_PIXELS)
    platform_images = {
        APP_ICON_FILE_NAME: app_icon,
        ADAPTIVE_FOREGROUND_FILE_NAME: adaptive_foreground,
        ADAPTIVE_BACKGROUND_FILE_NAME: Image.new("RGB", adaptive_size, icon_background_color),
        ADAPTIVE_MONOCHROME_FILE_NAME: to_silhouette(adaptive_foreground.getchannel("A")),
        SPLASH_ICON_FILE_NAME: to_rounded_tile(app_icon, SPLASH_ICON_SIZE_PIXELS),
        NOTIFICATION_ICON_FILE_NAME: build_notification_icon(motif_layer, motif_extent),
        FAVICON_FILE_NAME: to_rounded_tile(app_icon, FAVICON_SIZE_PIXELS),
    }
    for file_name, platform_image in platform_images.items():
        save_platform_image(platform_image, PLATFORM_IMAGES_FOLDER / file_name)
    return 0


def read_app_icon_background_color() -> Color:
    """brand-colors.json 의 appIconBackground (#RRGGBB) > (R, G, B)"""
    hex_color = json.loads(BRAND_COLORS_PATH.read_text(encoding="utf-8"))["appIconBackground"]
    return tuple(int(hex_color[offset : offset + 2], 16) for offset in (1, 3, 5))


def measure_source_background(source_image: Image.Image) -> tuple[Color, int]:
    """바깥 테두리 띠 > 채널별 중앙값(배경색), 채널별 1~99% 값 폭 중 가장 큰 것"""
    width, height = source_image.size
    border_pixels = max(1, round(min(width, height) * SOURCE_BORDER_RATIO))
    border_mask = Image.new("L", source_image.size, 255)
    border_mask.paste(0, (border_pixels, border_pixels, width - border_pixels, height - border_pixels))
    border_histogram = source_image.histogram(border_mask)
    channel_histograms = [border_histogram[start : start + 256] for start in (0, 256, 512)]

    background_color = tuple(find_histogram_level(histogram, 0.5) for histogram in channel_histograms)
    channel_spreads = [find_histogram_level(histogram, 0.99) - find_histogram_level(histogram, 0.01) for histogram in channel_histograms]
    return background_color, max(channel_spreads)


def find_histogram_level(histogram: list[int], ratio: float) -> int:
    """누적 개수가 전체의 ratio 에 처음 닿는 밝기 값 (0.5 = 중앙값)"""
    target_count = sum(histogram) * ratio
    cumulative_count = 0
    for level, count in enumerate(histogram):
        cumulative_count += count
        if cumulative_count >= target_count:
            return level
    return 255


def find_source_rejection_reason(
    source_size: tuple[int, int], source_background_color: Color, source_background_spread: int, icon_background_color: Color
) -> str | None:
    """원본을 못 쓰는 이유 (쓸 수 있으면 None)"""
    width, height = source_size
    if width != height or width < APP_ICON_SIZE_PIXELS:
        return f"source must be square and at least {APP_ICON_SIZE_PIXELS}px : {width}x{height}"
    if source_background_spread > SOURCE_BACKGROUND_NOISE_LEVELS:
        return f"source background is not a flat color : border spread {source_background_spread} > {SOURCE_BACKGROUND_NOISE_LEVELS}"
    mismatch = max(abs(source - icon) for source, icon in zip(source_background_color, icon_background_color))
    if mismatch > SOURCE_BACKGROUND_MISMATCH_LIMIT:
        colors = f"source background {source_background_color} is far from appIconBackground {icon_background_color}"
        return f"{colors} : update brand-colors.json and mobile/app.json first"
    return None


def separate_motif_layer(source_image: Image.Image, background_color: Color) -> Image.Image:
    """
    배경색과의 거리로 무늬만 남긴 투명 레이어 (RGB > RGBA)
    불투명도 = 그 픽셀을 배경 위에 그려 낼 수 있는 최소값 > 반투명 번짐·가장자리가 그대로 남음
    """
    opacity_tables = [build_channel_opacity_table(background_level) for background_level in background_color]
    source_bytes = source_image.tobytes()
    pixel_count = len(source_bytes) // 3
    motif_bytes = bytearray(pixel_count * 4)
    for pixel_index in range(pixel_count):
        channel_levels = source_bytes[pixel_index * 3 : pixel_index * 3 + 3]
        opacity = max(opacity_tables[channel_index][channel_levels[channel_index]] for channel_index in range(3))
        if opacity <= 0:
            continue
        motif_offset = pixel_index * 4
        # 배경을 걷어낸 색: 배경 + (픽셀 - 배경) / 불투명도
        for channel_index, background_level in enumerate(background_color):
            motif_level = background_level + (channel_levels[channel_index] - background_level) / opacity
            motif_bytes[motif_offset + channel_index] = min(255, max(0, round(motif_level)))
        motif_bytes[motif_offset + 3] = round(opacity * 255)
    return Image.frombytes("RGBA", source_image.size, bytes(motif_bytes))


def build_channel_opacity_table(background_level: int) -> list[float]:
    """
    채널 값(0~255) > 그 값을 내는 데 필요한 최소 불투명도 (배경보다 밝으면 흰색, 어두우면 검정을 섞는다고 봄)
    배경과의 차이에서 잡음 폭을 먼저 뺌 > 배경 잡음은 0, 그 위로는 끊김 없이 올라감. 끝까지 남은 폭이 좁은 방향은 0
    """
    opacity_table = []
    for level in range(256):
        # 배경에서 그 방향 끝(0 또는 255)까지 남은 폭
        headroom_levels = 255 - background_level if level > background_level else background_level
        distance_levels = abs(level - background_level) - SOURCE_BACKGROUND_NOISE_LEVELS
        is_distinguishable = distance_levels > 0 and headroom_levels >= SOURCE_MINIMUM_HEADROOM_LEVELS
        opacity_table.append(distance_levels / (headroom_levels - SOURCE_BACKGROUND_NOISE_LEVELS) if is_distinguishable else 0.0)
    return opacity_table


def measure_motif_extent(motif_layer: Image.Image) -> MotifExtent:
    """보이는 픽셀의 경계 상자 중심, 중심에서 가장 먼 보이는 픽셀까지의 거리"""
    visibility_mask = motif_layer.getchannel("A").point(lambda alpha: 255 if alpha >= MOTIF_VISIBLE_ALPHA else 0)
    left, top, right, bottom = visibility_mask.getbbox()
    center_x, center_y = (left + right) / 2, (top + bottom) / 2
    mask_bytes = visibility_mask.tobytes()
    farthest_distance_squared = 0.0
    for y in range(top, bottom):
        # 행마다 양 끝의 보이는 픽셀만 봄 (그 행에서 가장 먼 점은 항상 끝에 있음)
        row_bytes = mask_bytes[y * motif_layer.width + left : y * motif_layer.width + right]
        first_visible_x = row_bytes.find(255)
        if first_visible_x < 0:
            continue
        for x in (left + first_visible_x, left + row_bytes.rfind(255) + 1):
            distance_squared = (x - center_x) ** 2 + (y + 0.5 - center_y) ** 2
            farthest_distance_squared = max(farthest_distance_squared, distance_squared)
    return MotifExtent(center_x, center_y, math.sqrt(farthest_distance_squared))


def place_motif(motif_layer: Image.Image, motif_extent: MotifExtent, canvas_size_pixels: int, motif_radius_pixels: float) -> Image.Image:
    """무늬 중심을 캔버스 가운데에 두고, 무늬 반지름이 motif_radius_pixels 가 되게 확대·축소"""
    source_half_size = canvas_size_pixels / 2 * motif_extent.radius / motif_radius_pixels
    source_box = (
        round(motif_extent.center_x - source_half_size),
        round(motif_extent.center_y - source_half_size),
        round(motif_extent.center_x + source_half_size),
        round(motif_extent.center_y + source_half_size),
    )
    # 원본 밖으로 나간 부분은 투명으로 채워짐
    return motif_layer.crop(source_box).resize((canvas_size_pixels, canvas_size_pixels), Image.Resampling.LANCZOS)


def build_app_icon(motif_layer: Image.Image, icon_background_color: Color) -> Image.Image:
    """원본 구도 그대로, 배경만 브랜드 배경색으로 (알파 없는 RGB)"""
    background = Image.new("RGBA", motif_layer.size, icon_background_color + (255,))
    return Image.alpha_composite(background, motif_layer).convert("RGB")


def build_adaptive_foreground(motif_layer: Image.Image, motif_extent: MotifExtent) -> Image.Image:
    """투명 배경 위 무늬, 안전 영역(지름 66/108) 안에 전부 들어가는 크기"""
    safe_zone_radius_pixels = ADAPTIVE_ICON_SIZE_PIXELS * ADAPTIVE_SAFE_ZONE_DP / ADAPTIVE_CANVAS_DP / 2
    motif_radius_pixels = safe_zone_radius_pixels * ADAPTIVE_SAFE_ZONE_FILL_RATIO
    return place_motif(motif_layer, motif_extent, ADAPTIVE_ICON_SIZE_PIXELS, motif_radius_pixels)


def to_silhouette(alpha_channel: Image.Image) -> Image.Image:
    """알파 채널 > 흰색 한 색 실루엣 (색 정보 버림, 거의 꽉 찬 곳은 완전 불투명으로)"""
    silhouette = Image.new("RGBA", alpha_channel.size, SILHOUETTE_COLOR + (0,))
    silhouette.putalpha(stretch_alpha(alpha_channel, SILHOUETTE_ALPHA_RANGE))
    return silhouette


def stretch_alpha(alpha_channel: Image.Image, alpha_range: tuple[int, int]) -> Image.Image:
    """알파 구간 펴기: 아래 값 이하 0, 위 값 이상 255, 사이는 직선"""
    low_alpha, high_alpha = alpha_range
    return alpha_channel.point(lambda alpha: min(255, max(0, round((alpha - low_alpha) * 255 / (high_alpha - low_alpha)))))


def to_rounded_tile(app_icon: Image.Image, tile_size_pixels: int) -> Image.Image:
    """앱 아이콘 > 모서리가 둥근 타일 (모서리 밖 투명, 이미지를 꽉 채움)"""
    mask_size_pixels = tile_size_pixels * TILE_MASK_SUPERSAMPLE
    # 크게 그려 줄여서 모서리 계단 제거
    large_mask = Image.new("L", (mask_size_pixels, mask_size_pixels), 0)
    corner_radius_pixels = round(mask_size_pixels * TILE_CORNER_RADIUS_RATIO)
    ImageDraw.Draw(large_mask).rounded_rectangle((0, 0, mask_size_pixels - 1, mask_size_pixels - 1), corner_radius_pixels, fill=255)
    tile = app_icon.resize((tile_size_pixels, tile_size_pixels), Image.Resampling.LANCZOS).convert("RGBA")
    tile.putalpha(large_mask.resize((tile_size_pixels, tile_size_pixels), Image.Resampling.BOX))
    return tile


def build_notification_icon(motif_layer: Image.Image, motif_extent: MotifExtent) -> Image.Image:
    """상태 표시줄용 흰 실루엣: 무늬를 22dp 에 맞춤 > 옅은 번짐 버리기 > 선 굵히기 > 96px 로 줄임"""
    pixels_per_dp = NOTIFICATION_ICON_SIZE_PIXELS / NOTIFICATION_ICON_DP * NOTIFICATION_SUPERSAMPLE
    large_size_pixels = NOTIFICATION_ICON_SIZE_PIXELS * NOTIFICATION_SUPERSAMPLE
    large_motif = place_motif(motif_layer, motif_extent, large_size_pixels, NOTIFICATION_CONTENT_DP / 2 * pixels_per_dp)

    solid_alpha = stretch_alpha(large_motif.getchannel("A"), NOTIFICATION_FADE_CUT_ALPHA_RANGE)
    # 흐리게 번진 윤곽을 낮은 구간에서 자르면 선이 바깥으로 넓어짐 (24dp 에서 선이 끊기지 않게)
    blurred_alpha = solid_alpha.filter(ImageFilter.GaussianBlur(NOTIFICATION_THICKEN_BLUR_DP * pixels_per_dp))
    thick_alpha = stretch_alpha(blurred_alpha, NOTIFICATION_THICKEN_ALPHA_RANGE)
    icon_size = (NOTIFICATION_ICON_SIZE_PIXELS, NOTIFICATION_ICON_SIZE_PIXELS)
    return to_silhouette(thick_alpha.resize(icon_size, Image.Resampling.BOX))


def save_platform_image(platform_image: Image.Image, output_path: Path) -> None:
    """PNG 저장 (메타데이터 없음 > 실행할 때마다 같은 바이트)"""
    platform_image.save(output_path, format="PNG", optimize=True)
    size_text = f"{platform_image.width}x{platform_image.height} {platform_image.mode}"
    print(f"{LOG_PREFIX} wrote {output_path.relative_to(REPOSITORY_ROOT)} : {size_text}")


if __name__ == "__main__":
    sys.exit(main())

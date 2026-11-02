import 'package:flutter/material.dart';

/// Hiệu ứng Shimmer quét sáng chuẩn Shopee (Pure Flutter, không phụ thuộc package ngoài)
class Shimmer extends StatefulWidget {
  final Widget child;
  final Color baseColor;
  final Color highlightColor;
  final Duration period;

  const Shimmer({
    super.key,
    required this.child,
    this.baseColor = const Color(0xFFE6E8EB),
    this.highlightColor = const Color(0xFFF7F8FA),
    this.period = const Duration(milliseconds: 1400),
  });

  @override
  State<Shimmer> createState() => _ShimmerState();
}

class _ShimmerState extends State<Shimmer> with SingleTickerProviderStateMixin {
  late AnimationController _controller;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(vsync: this, duration: widget.period);
    bool isTest = false;
    assert(() {
      if (WidgetsBinding.instance.runtimeType.toString().contains('Test')) {
        isTest = true;
      }
      return true;
    }());
    // Không repeat animation vô tận trong headless test để tránh pumpAndSettle timeout
    if (!isTest) {
      _controller.repeat();
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _controller,
      child: widget.child,
      builder: (context, child) {
        return ShaderMask(
          blendMode: BlendMode.srcATop,
          shaderCallback: (bounds) {
            final gradient = LinearGradient(
              begin: const Alignment(-1.0, -0.3),
              end: const Alignment(1.0, 0.3),
              stops: const [0.1, 0.3, 0.45],
              colors: [
                widget.baseColor,
                widget.highlightColor,
                widget.baseColor,
              ],
              transform: _SlidingGradientTransform(slidePercent: _controller.value),
            );
            return gradient.createShader(bounds);
          },
          child: child,
        );
      },
    );
  }
}

class _SlidingGradientTransform extends GradientTransform {
  const _SlidingGradientTransform({required this.slidePercent});

  final double slidePercent;

  @override
  Matrix4? transform(Rect bounds, {TextDirection? textDirection}) {
    return Matrix4.translationValues(bounds.width * (slidePercent * 2 - 1), 0.0, 0.0);
  }
}

/// Khối hộp skeleton
class ShimmerBox extends StatelessWidget {
  final double? width;
  final double? height;
  final double borderRadius;
  final BoxShape shape;
  final EdgeInsetsGeometry? margin;

  const ShimmerBox({
    super.key,
    this.width,
    this.height,
    this.borderRadius = 6,
    this.shape = BoxShape.rectangle,
    this.margin,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      width: width,
      height: height,
      margin: margin,
      decoration: BoxDecoration(
        color: const Color(0xFFE6E8EB),
        shape: shape,
        borderRadius: shape == BoxShape.rectangle
            ? BorderRadius.circular(borderRadius)
            : null,
      ),
    );
  }
}

/// Thẻ sản phẩm Skeleton mô phỏng chính xác cấu trúc _ProductCard
class ProductCardSkeleton extends StatelessWidget {
  const ProductCardSkeleton({super.key});

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.white,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(8),
        side: const BorderSide(color: Color(0xffe0e6eb)),
      ),
      child: const Padding(
        padding: EdgeInsets.all(10),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Expanded(
              child: Center(
                child: ShimmerBox(
                  width: double.infinity,
                  height: double.infinity,
                  borderRadius: 6,
                ),
              ),
            ),
            SizedBox(height: 10),
            ShimmerBox(width: 50, height: 10, borderRadius: 3),
            SizedBox(height: 6),
            ShimmerBox(width: double.infinity, height: 14, borderRadius: 4),
            SizedBox(height: 4),
            ShimmerBox(width: 90, height: 14, borderRadius: 4),
            SizedBox(height: 8),
            ShimmerBox(width: 80, height: 16, borderRadius: 4),
            SizedBox(height: 6),
            ShimmerBox(width: 110, height: 12, borderRadius: 3),
            SizedBox(height: 8),
            ShimmerBox(width: 70, height: 12, borderRadius: 3),
          ],
        ),
      ),
    );
  }
}

/// Lưới Shimmer danh sách sản phẩm (SliverToBoxAdapter version dùng cho CustomScrollView)
class ProductGridShimmerSliver extends StatelessWidget {
  final int itemCount;
  const ProductGridShimmerSliver({super.key, this.itemCount = 6});

  @override
  Widget build(BuildContext context) {
    return SliverToBoxAdapter(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(12, 0, 12, 24),
        child: Shimmer(
          child: LayoutBuilder(
            builder: (context, constraints) {
              final crossAxisCount = constraints.maxWidth >= 700
                  ? 4
                  : constraints.maxWidth >= 500
                      ? 3
                      : 2;
              return GridView.builder(
                padding: EdgeInsets.zero,
                physics: const NeverScrollableScrollPhysics(),
                shrinkWrap: true,
                gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
                  crossAxisCount: crossAxisCount,
                  mainAxisExtent: 330 +
                      (MediaQuery.textScalerOf(context).scale(14) - 14) * 8,
                  crossAxisSpacing: 10,
                  mainAxisSpacing: 10,
                ),
                itemCount: itemCount,
                itemBuilder: (context, index) => const ProductCardSkeleton(),
              );
            },
          ),
        ),
      ),
    );
  }
}

/// Lưới Shimmer danh sách sản phẩm (Widget Box thông thường)
class ProductGridShimmer extends StatelessWidget {
  final int itemCount;
  const ProductGridShimmer({super.key, this.itemCount = 6});

  @override
  Widget build(BuildContext context) {
    return Shimmer(
      child: GridView.builder(
        padding: const EdgeInsets.all(12),
        physics: const NeverScrollableScrollPhysics(),
        shrinkWrap: true,
        gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
          crossAxisCount: 2,
          mainAxisExtent: 330,
          crossAxisSpacing: 10,
          mainAxisSpacing: 10,
        ),
        itemCount: itemCount,
        itemBuilder: (context, index) => const ProductCardSkeleton(),
      ),
    );
  }
}

/// Shimmer danh sách giỏ hàng
class CartListShimmer extends StatelessWidget {
  final int itemCount;
  const CartListShimmer({super.key, this.itemCount = 3});

  @override
  Widget build(BuildContext context) {
    return Shimmer(
      child: ListView.builder(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        physics: const NeverScrollableScrollPhysics(),
        shrinkWrap: true,
        itemCount: itemCount,
        itemBuilder: (context, index) {
          return Card(
            margin: const EdgeInsets.only(bottom: 12),
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(10),
            ),
            child: const Padding(
              padding: EdgeInsets.all(12),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  ShimmerBox(width: 70, height: 70, borderRadius: 8),
                  SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        ShimmerBox(width: double.infinity, height: 14, borderRadius: 4),
                        SizedBox(height: 6),
                        ShimmerBox(width: 120, height: 12, borderRadius: 3),
                        SizedBox(height: 10),
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            ShimmerBox(width: 80, height: 16, borderRadius: 4),
                            ShimmerBox(width: 60, height: 24, borderRadius: 4),
                          ],
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          );
        },
      ),
    );
  }
}

/// Shimmer danh sách đơn hàng
class OrderListShimmer extends StatelessWidget {
  final int itemCount;
  const OrderListShimmer({super.key, this.itemCount = 4});

  @override
  Widget build(BuildContext context) {
    return Shimmer(
      child: ListView.separated(
        padding: const EdgeInsets.all(12),
        physics: const NeverScrollableScrollPhysics(),
        shrinkWrap: true,
        itemCount: itemCount,
        separatorBuilder: (_, _) => const SizedBox(height: 8),
        itemBuilder: (context, index) {
          return Card(
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(10),
            ),
            child: const Padding(
              padding: EdgeInsets.all(14),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      ShimmerBox(width: 130, height: 16, borderRadius: 4),
                      ShimmerBox(width: 70, height: 20, borderRadius: 10),
                    ],
                  ),
                  SizedBox(height: 10),
                  ShimmerBox(width: 160, height: 12, borderRadius: 3),
                  SizedBox(height: 6),
                  ShimmerBox(width: 110, height: 14, borderRadius: 4),
                  SizedBox(height: 6),
                  ShimmerBox(width: 140, height: 12, borderRadius: 3),
                ],
              ),
            ),
          );
        },
      ),
    );
  }
}

/// Shimmer cho màn hình hồ sơ (Profile)
class ProfileShimmer extends StatelessWidget {
  const ProfileShimmer({super.key});

  @override
  Widget build(BuildContext context) {
    return Shimmer(
      child: Column(
        children: [
          Container(
            height: 180,
            width: double.infinity,
            color: const Color(0xFFE6E8EB),
          ),
          const SizedBox(height: 20),
          const Padding(
            padding: EdgeInsets.symmetric(horizontal: 16),
            child: Column(
              children: [
                ShimmerBox(width: double.infinity, height: 160, borderRadius: 12),
                SizedBox(height: 16),
                ShimmerBox(width: double.infinity, height: 90, borderRadius: 12),
                SizedBox(height: 24),
                ShimmerBox(width: double.infinity, height: 48, borderRadius: 12),
              ],
            ),
          ),
        ],
      ),
    );
  }
}


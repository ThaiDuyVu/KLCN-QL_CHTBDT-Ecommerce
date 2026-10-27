import 'dart:typed_data';
import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:ecommerce_app/features/checkout/data/payment_config_repository.dart';
import 'package:ecommerce_app/features/checkout/data/payment_method.dart';

class FixedAdapter implements HttpClientAdapter {
  FixedAdapter(this.body, {this.statusCode = 200});
  final String body;
  final int statusCode;
  final requests = <RequestOptions>[];

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? stream,
    Future<void>? cancel,
  ) async {
    requests.add(options);
    return ResponseBody.fromString(
      body,
      statusCode,
      headers: {
        Headers.contentTypeHeader: ['application/json'],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}

PaymentConfigRepository repositoryReturning(String body, {int statusCode = 200}) {
  final dio = Dio(BaseOptions(baseUrl: 'http://example.test'));
  dio.httpClientAdapter = FixedAdapter(body, statusCode: statusCode);
  return PaymentConfigRepository(client: dio);
}

void main() {
  test('VNPAY is hidden when the backend has it turned off', () {
    expect(
      availablePaymentMethods(vnpayEnabled: false),
      isNot(contains(PaymentMethodOption.vnpay)),
    );
    expect(
      availablePaymentMethods(vnpayEnabled: true),
      contains(PaymentMethodOption.vnpay),
    );
    expect(
      availablePaymentMethods(vnpayEnabled: false),
      contains(PaymentMethodOption.cod),
    );
  });

  test('Payment config reads the backend VNPAY flag', () async {
    expect(await repositoryReturning('{"enabled":true}').isVnpayEnabled(), isTrue);
    expect(await repositoryReturning('{"enabled":false}').isVnpayEnabled(), isFalse);
  });

  test('Payment config treats errors as VNPAY disabled', () async {
    expect(
      await repositoryReturning('{"message":"Forbidden"}', statusCode: 403).isVnpayEnabled(),
      isFalse,
    );
    expect(await repositoryReturning('[]').isVnpayEnabled(), isFalse);
  });
}

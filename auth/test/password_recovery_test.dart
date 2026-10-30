import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:tmb_agent_auth/password_recovery_screen.dart';

void main() {
  testWidgets('Invalid link cannot change a password', (tester) async {
    await tester.pumpWidget(MaterialApp(home: PasswordRecoveryScreen(
      sessionReady: false, onDone: () {},
    )));
    expect(find.text('DESAR CONTRASENYA'), findsNothing);
    expect(find.text('Tornar a l’accés'), findsOneWidget);
  });
  testWidgets('Validates both passwords before saving', (tester) async {
    var updates = 0;
    await tester.pumpWidget(MaterialApp(home: PasswordRecoveryScreen(
      sessionReady: true, onDone: () {},
      updatePassword: (_) async { updates++; },
    )));
    await tester.enterText(find.byType(TextField).at(0), 'abcdef');
    await tester.enterText(find.byType(TextField).at(1), 'different');
    await tester.tap(find.text('DESAR CONTRASENYA'));
    await tester.pumpAndSettle();
    expect(updates, 0);
    expect(find.text('Les contrasenyes no coincideixen.'), findsOneWidget);
    await tester.enterText(find.byType(TextField).at(1), 'abcdef');
    await tester.tap(find.text('DESAR CONTRASENYA'));
    await tester.pumpAndSettle();
    expect(updates, 1);
    expect(find.text('DESAR CONTRASENYA'), findsNothing);
    expect(find.textContaining('L’accés continua requerint autorització.'), findsOneWidget);
  });
  testWidgets('Failed update keeps the form available for retry', (tester) async {
    await tester.pumpWidget(MaterialApp(home: PasswordRecoveryScreen(
      sessionReady: true, onDone: () {},
      updatePassword: (_) async => throw Exception('network'),
    )));
    for (final field in find.byType(TextField).evaluate().toList().asMap().keys) {
      await tester.enterText(find.byType(TextField).at(field), 'abcdef');
    }
    await tester.tap(find.text('DESAR CONTRASENYA'));
    await tester.pumpAndSettle();
    expect(find.text('DESAR CONTRASENYA'), findsOneWidget);
    expect(find.textContaining('No s’ha pogut completar'), findsOneWidget);
  });
}

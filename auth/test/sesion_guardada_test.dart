import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:tmb_agent_auth/acceso_screen.dart';

void main() {
  for (final estado in ['aprobado', 'pendiente', 'rechazado', null]) {
    testWidgets('Restauración con estado $estado', (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          home: AccesoScreen(
            comprobarSesionGuardada: () async => estado,
            pantallaPrincipal: const Scaffold(body: Text('INICIO AUTORIZADO')),
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(
        find.text('INICIO AUTORIZADO'),
        estado == 'aprobado' ? findsOneWidget : findsNothing,
      );
      expect(
        find.text('ENTRAR'),
        estado == 'aprobado' ? findsNothing : findsOneWidget,
      );
    });
  }
  testWidgets(
    'Un fallo de conexión permite reintentar sin entrar ni pedir contraseña',
    (tester) async {
      var intentos = 0;
      await tester.pumpWidget(
        MaterialApp(
          home: AccesoScreen(
            comprobarSesionGuardada: () async {
              if (intentos++ == 0) throw Exception('offline');
              return 'aprobado';
            },
            pantallaPrincipal: const Scaffold(body: Text('INICIO AUTORIZADO')),
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('INICIO AUTORIZADO'), findsNothing);
      expect(find.text('ENTRAR'), findsNothing);
      await tester.tap(find.text('Tornar-ho a provar'));
      await tester.pumpAndSettle();
      expect(find.text('INICIO AUTORIZADO'), findsOneWidget);
    },
  );
}

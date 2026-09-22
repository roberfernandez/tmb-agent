import 'dart:js_interop';
import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:supabase_flutter/supabase_flutter.dart';
import 'acceso_screen.dart';

@JS('window.location.replace')
external void replaceLocation(JSString location);
@JS('window.location.search')
external JSString get locationSearch;

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await Supabase.initialize(
    url: 'https://hhenkvendzengggrgook.supabase.co',
    publishableKey: 'sb_publishable_QSPDTmh3fd0FH-VvAjH5KQ_Rfvzr2x_',
  );
  runApp(MaterialApp(
    title: 'TMB Agent',
    locale: const Locale('ca', 'ES'),
    supportedLocales: const [Locale('ca', 'ES')],
    localizationsDelegates: GlobalMaterialLocalizations.delegates,
    debugShowCheckedModeBanner: false,
    theme: ThemeData.dark().copyWith(scaffoldBackgroundColor: const Color(0xFF101010)),
    home: const AccesoScreen(pantallaPrincipal: EntradaAutorizada()),
  ));
}

class EntradaAutorizada extends StatefulWidget {
  const EntradaAutorizada({super.key});
  @override
  State<EntradaAutorizada> createState() => _EntradaAutorizadaState();
}

class _EntradaAutorizadaState extends State<EntradaAutorizada> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      var destination = '/tmb-agent/';
      final search = locationSearch.toDart;
      final params = Uri.splitQueryString(search.startsWith('?') ? search.substring(1) : search);
      final requested = Uri.tryParse(params['returnTo'] ?? '');
      if (requested != null && !requested.hasScheme && !requested.hasAuthority &&
          const ['/tmb-agent/', '/incidencias-l4/'].contains(requested.path) &&
          !requested.hasQuery) {
        destination = requested.toString();
      }
      replaceLocation(destination.toJS);
    });
  }

  @override
  Widget build(BuildContext context) => const Scaffold(
    body: Center(child: CircularProgressIndicator()),
  );
}

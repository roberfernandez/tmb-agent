import 'package:flutter/material.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

class AccesoScreen extends StatefulWidget {
  final Widget pantallaPrincipal;
  final Future<String?> Function()? comprobarSesionGuardada;

  const AccesoScreen({
    super.key,
    required this.pantallaPrincipal,
    this.comprobarSesionGuardada,
  });

  @override
  State<AccesoScreen> createState() => _AccesoScreenState();
}

class _AccesoScreenState extends State<AccesoScreen> {
  SupabaseClient get supabase => Supabase.instance.client;

  static const String emailAdmin = 'elodcoliv36@gmail.com';

  final emailController = TextEditingController();
  final passwordController = TextEditingController();

  bool cargando = false;
  bool ocultarPassword = true;
  String? error;
  String? mensaje;
  bool comprobandoSesion = true;
  bool falloRecuperacion = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) recuperarSesion();
    });
  }

  Future<String?> _comprobarSesionGuardada() async {
    final sesion = supabase.auth.currentSession;
    if (sesion == null) return null;
    if (sesion.isExpired) {
      await supabase.auth.refreshSession();
    }
    if (supabase.auth.currentSession == null) return null;
    final estado = await _estadoAcceso();
    if (estado != 'aprobado') {
      await supabase.auth.signOut(scope: SignOutScope.local);
    }
    return estado ?? 'sin_acceso';
  }

  Future<void> recuperarSesion() async {
    setState(() {
      comprobandoSesion = true;
      falloRecuperacion = false;
    });
    try {
      final estado =
          await (widget.comprobarSesionGuardada ?? _comprobarSesionGuardada)();
      if (!mounted) return;
      if (estado == 'aprobado') {
        _entrarEnApp();
        return;
      }
      setState(() {
        comprobandoSesion = false;
        if (estado != null) {
          mensaje = 'El teu compte no té l’accés aprovat actualment.';
        }
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        comprobandoSesion = false;
        falloRecuperacion = true;
      });
    }
  }

  @override
  void dispose() {
    emailController.dispose();
    passwordController.dispose();
    super.dispose();
  }

  bool _emailPermitido(String email) {
    final normalizado = email.trim().toLowerCase();

    return normalizado.endsWith('@tmb.cat') || normalizado == emailAdmin;
  }

  Future<String?> _estadoAcceso() async {
    // Validar la identidad en Auth; no confiar solo en el usuario persistido.
    final usuario = (await supabase.auth.getUser()).user;
    if (usuario == null) return null;
    final respuesta = await supabase
        .from('solicitudes_acceso')
        .select('estado')
        .eq('user_id', usuario.id)
        .order('created_at', ascending: false);

    final estados = List<Map<String, dynamic>>.from(respuesta);

    // Cualquier autorización aprobada prevalece.
    if (estados.any((fila) => fila['estado'] == 'aprobado')) {
      return 'aprobado';
    }

    // Si no está aprobado, comprobamos solicitudes pendientes.
    if (estados.any((fila) => fila['estado'] == 'pendiente')) {
      return 'pendiente';
    }

    // Si únicamente existen solicitudes rechazadas.
    if (estados.any((fila) => fila['estado'] == 'rechazado')) {
      return 'rechazado';
    }

    // Nunca ha solicitado acceso.
    return null;
  }

  void _entrarEnApp() {
    Navigator.of(context).pushReplacement(
      MaterialPageRoute(builder: (_) => widget.pantallaPrincipal),
    );
  }

  Future<void> iniciarSesion() async {
    final email = emailController.text.trim().toLowerCase();
    final password = passwordController.text;

    if (email.isEmpty || password.isEmpty) {
      setState(() {
        error = 'Introdueix el correu i la contrasenya.';
        mensaje = null;
      });
      return;
    }

    setState(() {
      cargando = true;
      error = null;
      mensaje = null;
    });

    try {
      await supabase.auth.signInWithPassword(email: email, password: password);

      final estado = await _estadoAcceso();

      if (!mounted) return;

      if (estado == 'aprobado') {
        _entrarEnApp();
        return;
      }

      await supabase.auth.signOut();

      if (!mounted) return;

      setState(() {
        cargando = false;

        if (estado == 'pendiente') {
          mensaje = 'La teva sol·licitud està pendent d’autorització.';
        } else if (estado == 'rechazado') {
          error = 'La teva sol·licitud d’accés ha estat rebutjada.';
        } else {
          error = 'Aquest usuari encara no té l’accés autoritzat.';
        }
      });
    } on AuthException {
      if (!mounted) return;

      setState(() {
        cargando = false;
        error = 'Correu o contrasenya incorrectes.';
      });
    } catch (e) {
      if (!mounted) return;

      setState(() {
        cargando = false;
        error = 'No s’ha pogut comprovar l’accés.';
      });
    }
  }

  Future<void> solicitarAcceso() async {
    final email = emailController.text.trim().toLowerCase();
    final password = passwordController.text;

    if (email.isEmpty || password.isEmpty) {
      setState(() {
        error = 'Introdueix el correu i la contrasenya que vulguis utilitzar.';
        mensaje = null;
      });
      return;
    }

    if (!_emailPermitido(email)) {
      setState(() {
        error = 'L’accés requereix un correu @tmb.cat.';
        mensaje = null;
      });
      return;
    }

    if (password.length < 6) {
      setState(() {
        error = 'La contrasenya ha de tenir almenys 6 caràcters.';
        mensaje = null;
      });
      return;
    }

    setState(() {
      cargando = true;
      error = null;
      mensaje = null;
    });

    try {
      // Primero intentamos iniciar sesión por si este usuario
      // ya fue creado anteriormente.
      try {
        await supabase.auth.signInWithPassword(
          email: email,
          password: password,
        );
      } on AuthException {
        // Si no existe todavía, Supabase crea internamente
        // su identidad con el correo y contraseña elegidos.
        final respuesta = await supabase.auth.signUp(
          email: email,
          password: password,
        );

        if (respuesta.session == null && supabase.auth.currentSession == null) {
          throw const AuthException(
            'No s’ha pogut iniciar la sessió del nou usuari.',
          );
        }
      }

      final usuario = supabase.auth.currentUser;

      if (usuario == null || supabase.auth.currentSession == null) {
        throw const AuthException('No s’ha pogut identificar l’usuari.');
      }

      // La Edge Function obtiene la identidad directamente
      // de la sesión autenticada. Flutter no le envía ni
      // user_id ni email para decidir quién solicita acceso.
      final respuestaFuncion = await supabase.functions.invoke(
        'solicitar-acceso',
        body: const {},
      );

      if (respuestaFuncion.status < 200 || respuestaFuncion.status >= 300) {
        throw Exception(
          'La funció ha retornat l’estat '
          '${respuestaFuncion.status}.',
        );
      }

      final datos = respuestaFuncion.data;

      if (datos is! Map) {
        throw Exception('Resposta no vàlida del servidor.');
      }

      final ok = datos['ok'] == true;
      final estado = datos['estado']?.toString();
      final errorServidor = datos['error']?.toString();

      if (!ok) {
        throw Exception(
          errorServidor ?? 'No s’ha pogut enviar la sol·licitud.',
        );
      }

      // Si ya estaba autorizado, entra directamente.
      if (estado == 'aprobado') {
        if (!mounted) return;
        _entrarEnApp();
        return;
      }

      // Para pendiente (nueva o ya existente), cerramos
      // la sesión local hasta que Róber la autorice.
      await supabase.auth.signOut();

      if (!mounted) return;

      setState(() {
        cargando = false;
        error = null;

        if (estado == 'pendiente') {
          mensaje =
              'Sol·licitud enviada correctament.\n\n'
              'Quan estigui autoritzada podràs entrar '
              'amb aquest correu i aquesta contrasenya.';
        } else {
          mensaje = 'Sol·licitud processada correctament.';
        }
      });
    } on AuthException catch (e) {
      await supabase.auth.signOut();

      if (!mounted) return;

      setState(() {
        cargando = false;
        mensaje = null;

        final texto = e.message.toLowerCase();

        if (texto.contains('password')) {
          error =
              'La contrasenya no és vàlida o no coincideix amb '
              'la que has utilitzat anteriorment.';
        } else if (texto.contains('already registered') ||
            texto.contains('already exists')) {
          error =
              'Aquest correu ja està registrat. Comprova '
              'la contrasenya i torna-ho a provar.';
        } else {
          error = 'No s’ha pogut sol·licitar l’accés: ${e.message}';
        }
      });
    } on FunctionException catch (e) {
      await supabase.auth.signOut();

      if (!mounted) return;

      setState(() {
        cargando = false;
        mensaje = null;
        error =
            'No s’ha pogut enviar la sol·licitud al servidor '
            '(codi ${e.status}).';
      });
    } catch (e) {
      await supabase.auth.signOut();

      if (!mounted) return;

      setState(() {
        cargando = false;
        mensaje = null;
        error = 'No s’ha pogut enviar la sol·licitud.';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    if (comprobandoSesion || falloRecuperacion) {
      return Scaffold(
        body: SafeArea(
          child: Center(
            child: Padding(
              padding: const EdgeInsets.all(24),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  if (comprobandoSesion) ...[
                    const CircularProgressIndicator(),
                    const SizedBox(height: 20),
                    const Text('Comprovant el teu accés…'),
                  ] else ...[
                    const Text(
                      'No s’ha pogut comprovar la sessió. Revisa la connexió i torna-ho a provar.',
                      textAlign: TextAlign.center,
                    ),
                    const SizedBox(height: 16),
                    FilledButton(
                      onPressed: recuperarSesion,
                      child: const Text('Tornar-ho a provar'),
                    ),
                    TextButton(
                      onPressed: () =>
                          setState(() => falloRecuperacion = false),
                      child: const Text('Identificar-me de nou'),
                    ),
                  ],
                ],
              ),
            ),
          ),
        ),
      );
    }
    return Scaffold(
      backgroundColor: const Color(0xFF101010),
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 430),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  const Icon(
                    Icons.train_rounded,
                    size: 82,
                    color: Colors.yellow,
                  ),
                  const SizedBox(height: 22),
                  const Text(
                    'TMB AGENT',
                    textAlign: TextAlign.center,
                    style: TextStyle(
                      fontSize: 28,
                      fontWeight: FontWeight.w900,
                      color: Colors.yellow,
                      letterSpacing: 0.8,
                    ),
                  ),
                  const SizedBox(height: 8),
                  const Text(
                    'Accés autoritzat',
                    textAlign: TextAlign.center,
                    style: TextStyle(fontSize: 16, color: Colors.white70),
                  ),
                  const SizedBox(height: 38),
                  TextField(
                    controller: emailController,
                    keyboardType: TextInputType.emailAddress,
                    textInputAction: TextInputAction.next,
                    autocorrect: false,
                    decoration: const InputDecoration(
                      labelText: 'Correu electrònic',
                      prefixIcon: Icon(Icons.email_outlined),
                      border: OutlineInputBorder(),
                    ),
                  ),
                  const SizedBox(height: 16),
                  TextField(
                    controller: passwordController,
                    obscureText: ocultarPassword,
                    textInputAction: TextInputAction.done,
                    onSubmitted: (_) {
                      if (!cargando) {
                        iniciarSesion();
                      }
                    },
                    decoration: InputDecoration(
                      labelText: 'Contrasenya',
                      prefixIcon: const Icon(Icons.lock_outline),
                      border: const OutlineInputBorder(),
                      suffixIcon: IconButton(
                        onPressed: () {
                          setState(() {
                            ocultarPassword = !ocultarPassword;
                          });
                        },
                        icon: Icon(
                          ocultarPassword
                              ? Icons.visibility_outlined
                              : Icons.visibility_off_outlined,
                        ),
                      ),
                    ),
                  ),
                  if (error != null) ...[
                    const SizedBox(height: 16),
                    Text(
                      error!,
                      textAlign: TextAlign.center,
                      style: const TextStyle(
                        color: Colors.redAccent,
                        fontSize: 14,
                      ),
                    ),
                  ],
                  if (mensaje != null) ...[
                    const SizedBox(height: 16),
                    Container(
                      padding: const EdgeInsets.all(14),
                      decoration: BoxDecoration(
                        color: Colors.green.withValues(alpha: 0.12),
                        borderRadius: BorderRadius.circular(12),
                        border: Border.all(
                          color: Colors.green.withValues(alpha: 0.45),
                        ),
                      ),
                      child: Text(
                        mensaje!,
                        textAlign: TextAlign.center,
                        style: const TextStyle(
                          color: Colors.white,
                          fontSize: 14,
                        ),
                      ),
                    ),
                  ],
                  const SizedBox(height: 24),
                  SizedBox(
                    height: 54,
                    child: FilledButton(
                      onPressed: cargando ? null : iniciarSesion,
                      child: cargando
                          ? const SizedBox(
                              width: 22,
                              height: 22,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            )
                          : const Text(
                              'ENTRAR',
                              style: TextStyle(
                                fontSize: 16,
                                fontWeight: FontWeight.w800,
                              ),
                            ),
                    ),
                  ),
                  const SizedBox(height: 12),
                  SizedBox(
                    height: 54,
                    child: OutlinedButton.icon(
                      onPressed: cargando ? null : solicitarAcceso,
                      icon: const Icon(Icons.person_add_alt_1_rounded),
                      label: const Text(
                        'SOL·LICITAR ACCÉS',
                        style: TextStyle(
                          fontSize: 15,
                          fontWeight: FontWeight.w800,
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(height: 14),
                  const Text(
                    'La primera vegada, introdueix el correu @tmb.cat '
                    'i la contrasenya que vulguis utilitzar i prem '
                    'SOL·LICITAR ACCÉS.',
                    textAlign: TextAlign.center,
                    style: TextStyle(
                      fontSize: 12,
                      height: 1.35,
                      color: Colors.white54,
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

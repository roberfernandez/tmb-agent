import 'package:flutter/material.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

class PasswordRecoveryScreen extends StatefulWidget {
  const PasswordRecoveryScreen({super.key, required this.sessionReady,
    required this.onDone, this.initialError, this.updatePassword});
  final bool sessionReady;
  final String? initialError;
  final VoidCallback onDone;
  final Future<void> Function(String)? updatePassword;

  @override
  State<PasswordRecoveryScreen> createState() => _PasswordRecoveryScreenState();
}

class _PasswordRecoveryScreenState extends State<PasswordRecoveryScreen> {
  final password = TextEditingController();
  final confirmation = TextEditingController();
  bool busy = false;
  bool saved = false;
  String? error;

  @override
  void dispose() {
    password.dispose();
    confirmation.dispose();
    super.dispose();
  }

  Future<void> save() async {
    if (busy || !widget.sessionReady) return;
    if (password.text.length < 6 || password.text != confirmation.text) {
      setState(() => error = password.text.length < 6
        ? 'La contrasenya ha de tenir almenys 6 caràcters.'
        : 'Les contrasenyes no coincideixen.');
      return;
    }
    setState(() { busy = true; error = null; });
    try {
      if (widget.updatePassword != null) {
        await widget.updatePassword!(password.text);
      } else {
        final auth = Supabase.instance.client.auth;
        // Validate the recovery identity remotely before updating the password.
        await auth.getUser();
        await auth.updateUser(UserAttributes(password: password.text));
        await auth.signOut();
      }
      password.clear();
      confirmation.clear();
      if (mounted) setState(() => saved = true);
    } catch (_) {
      if (mounted) setState(() => error = 'No s’ha pogut completar el canvi. Si l’enllaç ha caducat, demana’n un de nou.');
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    body: SafeArea(child: Center(child: SingleChildScrollView(
      padding: const EdgeInsets.all(24),
      child: ConstrainedBox(constraints: const BoxConstraints(maxWidth: 430),
        child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          const Icon(Icons.lock_reset, size: 64, color: Colors.yellow),
          const SizedBox(height: 20),
          const Text('Canviar la contrasenya', textAlign: TextAlign.center,
            style: TextStyle(fontSize: 24, fontWeight: FontWeight.bold)),
          const SizedBox(height: 20),
          if (!widget.sessionReady) Text(widget.initialError ??
            'L’enllaç no és vàlid o ha caducat. Torna a l’accés i demana’n un de nou.',
            textAlign: TextAlign.center),
          if (saved) const Text('Contrasenya canviada. Ja pots tornar a identificar-te. L’accés continua requerint autorització.', textAlign: TextAlign.center),
          if (widget.sessionReady && !saved) ...[
            TextField(controller: password, obscureText: true,
              autofillHints: const [AutofillHints.newPassword],
              decoration: const InputDecoration(labelText: 'Nova contrasenya', border: OutlineInputBorder())),
            const SizedBox(height: 16),
            TextField(controller: confirmation, obscureText: true,
              onSubmitted: (_) => save(),
              decoration: const InputDecoration(labelText: 'Repeteix la contrasenya', border: OutlineInputBorder())),
            if (error != null) Padding(padding: const EdgeInsets.symmetric(vertical: 16),
              child: Text(error!, style: const TextStyle(color: Colors.redAccent))),
            const SizedBox(height: 20),
            FilledButton(onPressed: busy ? null : save,
              child: Text(busy ? 'Desant…' : 'DESAR CONTRASENYA')),
          ],
          TextButton(onPressed: busy ? null : widget.onDone, child: const Text('Tornar a l’accés')),
        ])),
    ))),
  );
}

-- Browser clients only read their own request. Creation and resolution stay
-- in solicitar-acceso / telegram-bot, which validate identity on the server.
-- Removing the unused self-referential INSERT policy fixes its recursion
-- without introducing a privileged function or allowing self-approval.
DROP POLICY IF EXISTS "Enable insert for users based on user_id"
  ON public.solicitudes_acceso;
REVOKE INSERT, UPDATE, DELETE, TRUNCATE, REFERENCES, TRIGGER
  ON public.solicitudes_acceso FROM anon, authenticated;
REVOKE SELECT ON public.solicitudes_acceso FROM anon;
GRANT SELECT ON public.solicitudes_acceso TO authenticated;

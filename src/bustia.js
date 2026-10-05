import { modules } from './modules.js?v=bustia-v1';
import { SESSION_KEY } from './session.js';

const URL = 'https://hhenkvendzengggrgook.supabase.co';
const PUBLIC_KEY = 'sb_publishable_QSPDTmh3fd0FH-VvAjH5KQ_Rfvzr2x_';
const FUNCTION_URL = `${URL}/functions/v1/tmb-agent-bustia`;

function currentSession() {
  try {
    const value = JSON.parse(localStorage.getItem(SESSION_KEY));
    if (value?.access_token) return value;
  } catch {}
  return null;
}

async function callBustia(payload, request = fetch) {
  const session = currentSession();
  if (!session) throw new Error('Sessió no disponible');

  const response = await request(FUNCTION_URL, {
    method: 'POST',
    cache: 'no-store',
    headers: {
      apikey: PUBLIC_KEY,
      Authorization: `Bearer ${session.access_token}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(payload),
  });

  const data = await response.json().catch(() => ({}));
  if (!response.ok || !data?.ok) {
    throw new Error(data?.error || `Error ${response.status}`);
  }
  return data;
}

function node(tag, className, text) {
  const result = document.createElement(tag);
  if (className) result.className = className;
  if (text !== undefined && text !== null) result.textContent = text;
  return result;
}

function option(value, label) {
  const item = document.createElement('option');
  item.value = value;
  item.textContent = label;
  return item;
}

export function categoryLabel(value) {
  return ({
    millora: 'Millora d’una miniapp',
    nova_miniapp: 'Nova miniapp',
    nova_funcio: 'Nova funció',
    altres: 'Altres',
  })[value] || 'Petició';
}

export function statusLabel(value) {
  return ({
    oberta: 'Oberta',
    en_estudi: 'En estudi',
    acceptada: 'Acceptada',
    en_desenvolupament: 'En desenvolupament',
    feta: 'Feta',
    descartada: 'Descartada',
  })[value] || value || '';
}

export function availableModules() {
  return modules
    .filter(module => module.id !== 'bustia')
    .map(module => ({ id: module.id, name: module.name }));
}

function moduleSelect(name) {
  const select = node('select', 'bustia-select');
  select.name = name;
  select.append(option('', 'General / TMB Agent'));
  availableModules().forEach(module => select.append(option(module.id, module.name)));
  return select;
}

function field(labelText, control) {
  const wrapper = node('label', 'bustia-field');
  wrapper.append(node('span', 'bustia-label', labelText), control);
  return wrapper;
}

function liveStatus() {
  const status = node('p', 'bustia-status');
  status.setAttribute('role', 'status');
  status.setAttribute('aria-live', 'polite');
  return status;
}

function setBusy(button, busy, busyText = 'Enviant…') {
  if (!button.dataset.label) button.dataset.label = button.textContent;
  button.disabled = busy;
  button.textContent = busy ? busyText : button.dataset.label;
}

export function proposalCard(item, onVote, onStatus) {
  const article = node('article', 'bustia-proposal');
  article.dataset.proposalId = String(item.id);

  const meta = node('div', 'bustia-proposal-meta');
  meta.append(
    node('span', 'bustia-pill', categoryLabel(item.categoria)),
    node('span', `bustia-pill bustia-state bustia-state-${item.estat}`, statusLabel(item.estat)),
  );
  if (item.modul) {
    const module = modules.find(candidate => candidate.id === item.modul);
    meta.append(node('span', 'bustia-module', module?.name || item.modul));
  }

  const title = node('h3', '', item.titol);
  article.append(meta, title);
  if (item.detall) article.append(node('p', 'bustia-detail', item.detall));

  const footer = node('div', 'bustia-proposal-footer');
  const count = node(
    'span',
    'bustia-vote-count',
    `${item.vots} suport${item.vots === 1 ? '' : 's'}`,
  );
  const vote = node(
    'button',
    item.meu_vot ? 'bustia-vote is-active' : 'bustia-vote',
    item.meu_vot ? '✓ M’hi sumo' : 'M’hi sumo',
  );
  vote.type = 'button';
  vote.setAttribute('aria-pressed', item.meu_vot ? 'true' : 'false');

  vote.addEventListener('click', async () => {
    setBusy(vote, true, 'Guardant…');
    try {
      const data = await onVote(item);
      item.meu_vot = Boolean(data.meu_vot);
      item.vots = Number(data.vots) || 0;
      vote.classList.toggle('is-active', item.meu_vot);
      vote.setAttribute('aria-pressed', item.meu_vot ? 'true' : 'false');
      vote.dataset.label = item.meu_vot ? '✓ M’hi sumo' : 'M’hi sumo';
      count.textContent = `${item.vots} suport${item.vots === 1 ? '' : 's'}`;
    } catch (error) {
      count.textContent = error.message || 'No s’ha pogut guardar';
    } finally {
      setBusy(vote, false);
    }
  });

  footer.append(count);
  if (item.estat !== 'feta') footer.append(vote);
  article.append(footer);
  if (onStatus) {
    const select = node('select', 'bustia-select');
    for (const state of ['oberta', 'en_estudi', 'acceptada', 'en_desenvolupament', 'feta', 'descartada']) {
      select.append(option(state, statusLabel(state)));
    }
    select.value = item.estat;
    const save = node('button', 'button', 'Desar estat');
    save.type = 'button';
    const status = liveStatus();
    save.addEventListener('click', async () => {
      setBusy(save, true, 'Desant…');
      select.disabled = true;
      status.textContent = '';
      try {
        await onStatus(item, select.value);
      } catch (error) {
        status.textContent = error.message || 'No s’ha pogut desar l’estat.';
      } finally {
        select.disabled = false;
        setBusy(save, false);
      }
    });
    article.append(field(`Gestionar estat · ${item.titol}`, select), save, status);
  }
  return article;
}

async function renderProposalList(list, status) {
  list.replaceChildren(node('p', 'notice', 'Carregant peticions…'));
  try {
    const data = await callBustia({ action: 'list' });
    const proposals = Array.isArray(data.propostes) ? data.propostes : [];
    list.replaceChildren();

    if (!proposals.length) {
      list.append(node('p', 'notice', 'Encara no hi ha peticions. Pots crear la primera.'));
      return;
    }

    proposals.forEach(item => {
      list.append(proposalCard(item, async proposal => {
        const action = proposal.meu_vot ? 'unvote' : 'vote';
        const result = await callBustia({ action, proposta_id: proposal.id });
        if (result.telegram === false) {
          status.textContent = 'Canvi guardat, però no s’ha pogut enviar l’avís per Telegram.';
        } else {
          status.textContent = action === 'vote'
            ? 'T’has sumat a la petició.'
            : 'Has retirat el teu suport.';
        }
        return result;
      }, data.can_manage === true ? async (proposal, estat) => {
        await callBustia({ action: 'set_status', proposta_id: proposal.id, estat });
        status.textContent = `Estat desat: ${statusLabel(estat)}.`;
        await renderProposalList(list, status);
      } : undefined));
    });
  } catch (error) {
    list.replaceChildren(node('p', 'notice', error.message || 'No s’han pogut carregar les peticions.'));
  }
}

function renderContactPanel() {
  const panel = node('section', 'bustia-section');
  panel.append(
    node('h2', '', 'Contacta'),
    node('p', 'bustia-intro', 'Envia una consulta o descriu un error. El missatge arriba directament per Telegram.'),
  );

  const form = node('form', 'bustia-form');
  const type = node('select', 'bustia-select');
  type.name = 'tipus';
  type.append(option('error', '🐞 He trobat un error'), option('consulta', '❓ Tinc una consulta'));

  const module = moduleSelect('modul');
  const message = node('textarea', 'bustia-textarea');
  message.name = 'missatge';
  message.rows = 6;
  message.maxLength = 2000;
  message.required = true;
  message.placeholder = 'Descriu-ho amb el màxim detall que puguis…';

  const submit = node('button', 'button bustia-submit', 'Enviar');
  submit.type = 'submit';
  const status = liveStatus();

  form.append(
    field('Tipus', type),
    field('Miniapp relacionada', module),
    field('Descripció', message),
    submit,
    status,
  );

  form.addEventListener('submit', async event => {
    event.preventDefault();
    const text = message.value.trim();
    if (text.length < 3) {
      status.textContent = 'Escriu una descripció una mica més detallada.';
      message.focus();
      return;
    }

    setBusy(submit, true);
    status.textContent = '';
    try {
      await callBustia({
        action: 'message',
        tipus: type.value,
        modul: module.value,
        missatge: text,
      });
      message.value = '';
      status.textContent = '✓ Missatge enviat.';
    } catch (error) {
      status.textContent = error.message || 'No s’ha pogut enviar.';
    } finally {
      setBusy(submit, false);
    }
  });

  panel.append(form);
  return panel;
}

function renderProposalsPanel() {
  const panel = node('section', 'bustia-section');
  panel.append(
    node('h2', '', 'Peticions'),
    node('p', 'bustia-intro', 'Consulta què està demanant la resta i suma-t’hi amb un toc.'),
  );

  const newButton = node('button', 'button bustia-new', '+ Nova petició');
  newButton.type = 'button';

  const form = node('form', 'bustia-form bustia-proposal-form is-hidden');
  const category = node('select', 'bustia-select');
  category.name = 'categoria';
  category.append(
    option('millora', 'Millora d’una miniapp'),
    option('nova_miniapp', 'Nova miniapp'),
    option('nova_funcio', 'Nova funció'),
    option('altres', 'Altres'),
  );

  const module = moduleSelect('modul');
  const title = node('input', 'bustia-input');
  title.type = 'text';
  title.name = 'titol';
  title.maxLength = 140;
  title.required = true;
  title.placeholder = 'Ex.: Comptabilitzar també els Avisos';

  const detail = node('textarea', 'bustia-textarea');
  detail.name = 'detall';
  detail.rows = 4;
  detail.maxLength = 1200;
  detail.placeholder = 'Detall opcional';

  const submit = node('button', 'button bustia-submit', 'Publicar petició');
  submit.type = 'submit';
  const formStatus = liveStatus();

  form.append(
    field('Categoria', category),
    field('Miniapp relacionada', module),
    field('Petició', title),
    field('Detall', detail),
    submit,
    formStatus,
  );

  newButton.addEventListener('click', () => {
    const hidden = form.classList.toggle('is-hidden');
    newButton.textContent = hidden ? '+ Nova petició' : 'Tancar';
    if (!hidden) title.focus();
  });

  const listStatus = liveStatus();
  const list = node('div', 'bustia-proposal-list');

  form.addEventListener('submit', async event => {
    event.preventDefault();
    const cleanTitle = title.value.trim();
    if (cleanTitle.length < 3) {
      formStatus.textContent = 'Escriu una petició una mica més concreta.';
      title.focus();
      return;
    }

    setBusy(submit, true, 'Publicant…');
    formStatus.textContent = '';
    try {
      const result = await callBustia({
        action: 'proposal',
        categoria: category.value,
        modul: module.value,
        titol: cleanTitle,
        detall: detail.value.trim(),
      });
      title.value = '';
      detail.value = '';
      form.classList.add('is-hidden');
      newButton.textContent = '+ Nova petició';
      listStatus.textContent = result.telegram === false
        ? '✓ Petició publicada, però no s’ha pogut enviar l’avís per Telegram.'
        : '✓ Petició publicada. Ja compta amb el teu suport.';
      await renderProposalList(list, listStatus);
    } catch (error) {
      formStatus.textContent = error.message || 'No s’ha pogut publicar.';
    } finally {
      setBusy(submit, false);
    }
  });

  panel.append(newButton, form, listStatus, list);
  renderProposalList(list, listStatus);
  return panel;
}

export function renderBustia() {
  const root = node('section', 'bustia');
  const tabs = node('div', 'bustia-tabs');
  const contactButton = node('button', 'bustia-tab is-active', 'Missatge');
  const proposalsButton = node('button', 'bustia-tab', 'Peticions');
  contactButton.type = proposalsButton.type = 'button';

  const body = node('div', 'bustia-body');
  const contact = renderContactPanel();
  const proposals = renderProposalsPanel();
  proposals.hidden = true;

  function show(which) {
    const contactActive = which === 'contact';
    contact.hidden = !contactActive;
    proposals.hidden = contactActive;
    contactButton.classList.toggle('is-active', contactActive);
    proposalsButton.classList.toggle('is-active', !contactActive);
    contactButton.setAttribute('aria-selected', contactActive ? 'true' : 'false');
    proposalsButton.setAttribute('aria-selected', contactActive ? 'false' : 'true');
  }

  contactButton.addEventListener('click', () => show('contact'));
  proposalsButton.addEventListener('click', () => show('proposals'));

  tabs.setAttribute('role', 'tablist');
  contactButton.setAttribute('role', 'tab');
  proposalsButton.setAttribute('role', 'tab');
  tabs.append(contactButton, proposalsButton);
  body.append(contact, proposals);
  root.append(tabs, body);
  return root;
}

export { callBustia };

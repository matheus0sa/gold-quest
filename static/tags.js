// Modal de tags: adiciona e remove sem recarregar a página, para não perder o
// que já foi digitado no formulário da tarefa. Sem JavaScript, os formulários
// do modal funcionam normalmente (recarregando a página).
(function () {
  const dialog = document.getElementById('tag-dialog');
  if (!dialog) return;
  const list = document.getElementById('tag-list');
  const addForm = document.getElementById('tag-add');
  const errorBox = document.getElementById('tag-error');
  const options = document.getElementById('tag-options');

  document.querySelectorAll('[data-open-tags]').forEach(btn =>
    btn.addEventListener('click', () => {
      errorBox.hidden = true;
      dialog.showModal();
      addForm.querySelector('input[name="name"]').focus();
    })
  );

  function showError(message) {
    errorBox.textContent = message;
    errorBox.hidden = false;
  }

  async function send(form) {
    const response = await fetch(form.action, {
      method: 'POST',
      body: new FormData(form),
      headers: { Accept: 'application/json' },
    });
    const data = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(data.error || 'Não foi possível salvar. Tente de novo.');
    return data;
  }

  // Cria a opção de tag no formulário da tarefa (antes do botão "Gerenciar")
  function addOption(tag) {
    if (!options) return;
    const label = document.createElement('label');
    label.className = 'tag-option';
    const input = document.createElement('input');
    input.type = 'radio';
    input.name = 'tag_id';
    input.value = tag.id;
    const span = document.createElement('span');
    span.textContent = tag.name;
    label.append(input, span);
    options.insertBefore(label, options.querySelector('[data-open-tags]'));
  }

  function removeOption(id) {
    if (!options) return;
    const input = options.querySelector(`input[name="tag_id"][value="${id}"]`);
    if (!input) return;
    if (input.checked) options.querySelector('input[name="tag_id"][value=""]').checked = true;
    input.closest('label').remove();
  }

  function addListItem(tag) {
    const li = document.createElement('li');
    const name = document.createElement('span');
    name.textContent = tag.name;
    const form = document.createElement('form');
    form.method = 'post';
    form.action = list.dataset.deleteUrl.replace('/0/', `/${tag.id}/`);
    form.dataset.tagId = tag.id;
    form.dataset.tagName = tag.name;
    const button = document.createElement('button');
    button.className = 'icon-btn';
    button.type = 'submit';
    button.textContent = '✕';
    button.setAttribute('aria-label', `Remover tag ${tag.name}`);
    form.append(button);
    li.append(name, form);
    list.append(li);
  }

  addForm.addEventListener('submit', async event => {
    event.preventDefault();
    errorBox.hidden = true;
    try {
      const tag = await send(addForm);
      addListItem(tag);
      addOption(tag);
      addForm.reset();
    } catch (err) {
      showError(err.message);
    }
  });

  list.addEventListener('submit', async event => {
    event.preventDefault();
    const form = event.target;
    if (!confirm(`Remover a tag "${form.dataset.tagName}"? As tarefas com ela ficam sem tag.`)) return;
    errorBox.hidden = true;
    try {
      await send(form);
      removeOption(form.dataset.tagId);
      form.closest('li').remove();
    } catch (err) {
      showError(err.message);
    }
  });
})();

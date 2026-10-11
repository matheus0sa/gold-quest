// Formulário de tarefa (criar e editar).
// Mensal e dias da semana são exclusivos: marcar um desmarca o outro.
// Ao escolher repetição, o prazo deixa de fazer sentido.
(function () {
  const dias = document.querySelectorAll('input[name="weekdays"]');
  const mensal = document.querySelector('input[name="repeat"]');
  const prazo = document.getElementById('due-field');
  const dica = document.querySelector('#form-hint[data-dynamic]');  // dica que acompanha a escolha
  if (!mensal) return;  // edição de tarefa única: não há repetição

  function atualizar() {
    const semanal = [...dias].some(d => d.checked);
    const repete = semanal || mensal.checked;
    if (prazo) {
      prazo.hidden = repete;
      prazo.querySelector('input').disabled = repete;
    }
    if (dica) {
      dica.textContent = mensal.checked
        ? 'Aparece em "Este mês" até ser feita e dá gold uma vez por mês.'
        : semanal
          ? 'Aparece em "Hoje" nos dias marcados e dá gold toda vez.'
          : 'No prazo: +20% de gold. Atrasada: metade.';
    }
  }

  dias.forEach(d => d.addEventListener('change', () => {
    if (d.checked) mensal.checked = false;
    atualizar();
  }));
  mensal.addEventListener('change', () => {
    if (mensal.checked) dias.forEach(d => { d.checked = false; });
    atualizar();
  });
  atualizar();
})();

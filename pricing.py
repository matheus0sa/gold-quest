"""
Preços dinâmicos da loja.

O preço é calculado na hora, a partir do histórico de compras do item.
Nada fica salvo: mudar uma constante aqui vale imediatamente para tudo.
"""
from dataclasses import dataclass
from datetime import date, datetime

# --- Ajuste fino: mude aqui depois de usar por uma ou duas semanas ---
IMPACTO_COMPRA = 0.10      # cada compra encarece 10%...
DIAS_PARA_ESQUECER = 10    # ...e esse efeito some aos poucos em 10 dias
PROMO_APOS_DIAS = 5        # sem comprar por 5 dias, entra em promoção
PROMO_POR_DIA = 0.05       # desconto cresce 5% por dia...
PROMO_MAXIMA = 0.30        # ...até no máximo 30%
TETO = 1.8                 # preço nunca passa de 1,8x o base
PISO = 0.7                 # nem fica abaixo de 0,7x
ARREDONDAR_PARA = 5        # preços em múltiplos de 5


@dataclass
class PriceInfo:
    base: int
    price: int
    trend: str          # "up", "down" ou "base"
    reason: str         # explicação curta para mostrar na loja


def _as_date(value):
    return value.date() if isinstance(value, datetime) else value


def calcular_preco(base, compras, criado_em, hoje):
    """
    base: preço base do item
    compras: datas/datetimes das compras desse item (qualquer ordem)
    criado_em: quando o item entrou na loja (usado se nunca foi comprado)
    hoje: data de referência
    """
    dias_compras = [(hoje - _as_date(c)).days for c in compras]
    dias_compras = [d for d in dias_compras if d >= 0]

    # 1) Cada compra recente soma um efeito que enfraquece com o tempo
    alta = 0.0
    recentes = 0
    for dias in dias_compras:
        peso = 1 - dias / DIAS_PARA_ESQUECER
        if peso > 0:
            alta += IMPACTO_COMPRA * peso
            recentes += 1

    # 2) Promoção para item esquecido
    ultima = min(dias_compras) if dias_compras else None
    referencia = ultima if ultima is not None else (hoje - _as_date(criado_em or hoje)).days
    desconto = 0.0
    if referencia >= PROMO_APOS_DIAS:
        desconto = min(PROMO_MAXIMA, PROMO_POR_DIA * (referencia - PROMO_APOS_DIAS + 1))

    # 3) Limites e arredondamento
    mult = max(PISO, min(TETO, 1 + alta - desconto))
    preco = max(ARREDONDAR_PARA, round(base * mult / ARREDONDAR_PARA) * ARREDONDAR_PARA)

    if preco > base:
        vezes = "1 vez" if recentes == 1 else f"{recentes} vezes"
        return PriceInfo(base, preco, "up", f"Comprado {vezes} nos últimos {DIAS_PARA_ESQUECER} dias")
    if preco < base:
        if ultima is None:
            motivo = "Promoção: nunca comprado"
        else:
            motivo = f"Promoção: {referencia} dias sem usar"
        return PriceInfo(base, preco, "down", motivo)
    return PriceInfo(base, preco, "base", "Preço normal")

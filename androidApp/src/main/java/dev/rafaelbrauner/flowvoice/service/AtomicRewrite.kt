package dev.rafaelbrauner.flowvoice.service

// Quando o reparo automático (P142) pode reescrever o campo inteiro com ACTION_SET_TEXT (Y5). O texto
// novo é montado a partir do `node.text`, então tudo o que o nó não mostra se perde: o resto de um campo
// exposto só em parte (WebView com contenteditable), a formatação que ele carrega e o histórico de
// desfazer. O reparo conserta um caractere — o espaço da emenda que sumiu, o ponto que sobrou —, e o
// estrago possível é a nota inteira, então a regra é estreita: só com as três condições abaixo, e fora
// delas o campo fica como está e o desvio só vai para o log, como já acontece quando a leitura falha.
//  1. o editor é o EditText da plataforma (classe de acessibilidade `android.widget.EditText`);
//  2. o texto do nó é simples: sem span nenhum (link, imagem, trecho clicável);
//  3. o nó mostra o campo inteiro: o que o teclado vê do campo, pedido com folga para os dois lados,
//     começa na posição 0, é igual ao texto do nó e tem a mesma seleção.
// A formatação que o nó não expõe (negrito de um editor rico que se apresenta como EditText) continua
// invisível daqui: é o que falta medir no aparelho.
object AtomicRewrite {
    const val PLAIN_EDITOR_CLASS = "android.widget.EditText"

    enum class Skip(val reason: String) {
        NotPlainEditor("editor"),
        Formatted("formatado"),
        Partial("parcial")
    }

    class Field(val text: String, val offset: Int, val selectionStart: Int, val selectionEnd: Int)

    fun skip(
        className: CharSequence?,
        nodeText: CharSequence?,
        nodeHasSpans: Boolean,
        nodeSelectionStart: Int,
        nodeSelectionEnd: Int,
        field: Field?
    ): Skip? = when {
        className?.toString() != PLAIN_EDITOR_CLASS -> Skip.NotPlainEditor
        nodeText == null || nodeHasSpans -> Skip.Formatted
        field == null || field.offset != 0 || field.text != nodeText.toString() -> Skip.Partial
        field.selectionStart != nodeSelectionStart || field.selectionEnd != nodeSelectionEnd -> Skip.Partial
        else -> null
    }
}

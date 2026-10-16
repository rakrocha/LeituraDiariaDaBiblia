package com.example.leituradiariadabiblia
/**
 * Programa "Leitura da Bíblia" transcrito do PDF enviado.
 * Cada item representa um grupo de capítulos do programa original.
 */
data class BibleReading(
    val id: String,
    val section: String,
    val book: String,
    val chapters: String
) {
    val reference: String get() = "$book $chapters"
}

/**
 * Os grupos são mantidos na ordem em que aparecem no programa.
 * O identificador é estável e será usado para salvar o histórico local.
 */
object BibleReadingData {
    private val source = """
OS ESCRITOS DE MOISÉS|Gênesis|1-3;4-7;8-11;12-15;16-18;19-22;23-24;25-27;28-30;31-32;33-34;35-37;38-40;41-42;43-45;46-48;49-50
OS ESCRITOS DE MOISÉS|Êxodo|1-4;5-7;8-10;11-13;14-15;16-18;19-21;22-25;26-28;29-30;31-33
OS ESCRITOS DE MOISÉS|Levítico|1-4;5-7;8-10;11-13;14-15;16-18;19-21;22-23;24-25;26-27
OS ESCRITOS DE MOISÉS|Números|1-3;4-6;7-9;10-12;13-15;16-18;19-21;22-24;25-27;28-30;31-32;33-36
OS ESCRITOS DE MOISÉS|Deuteronômio|1-2;3-4;5-7;8-10;11-13;14-16;17-19;20-22;23-26;27-28;29-31;32;33-34
ISRAEL ENTRA NA TERRA PROMETIDA|Josué|1-4;5-7;8-9;10-12;13-15;16-18;19-21;22-24
ISRAEL ENTRA NA TERRA PROMETIDA|Juízes|1-2;3-5;6-7;8-9;10-11;12-13;14-16;17-19;20-22
ISRAEL ENTRA NA TERRA PROMETIDA|Rute|1-4
QUANDO OS REIS GOVERNAVAM ISRAEL|1 Samuel|1-2;3-6;7-9;10-12;13-14;15-16;17-18;19-21;22-24;25-27;28-31
QUANDO OS REIS GOVERNAVAM ISRAEL|2 Samuel|1-2;3-5;6-8;9-12;13-14;15-16;17-18;19-20;21-22;23-24
QUANDO OS REIS GOVERNAVAM ISRAEL|1 Reis|1-2;3-5;6-7;8;9-10;11-12;13-14;15-17;18-19;20-21;22
QUANDO OS REIS GOVERNAVAM ISRAEL|2 Reis|1-3;4-5;6-8;9-10;11-13;14-15;16-17;18-19;20-22;23-25
QUANDO OS REIS GOVERNAVAM ISRAEL|1 Crônicas|1-2;3-5;6-7;8-10;11-12;13-15;16-17;18-20;21-23;24-26;27-29
QUANDO OS REIS GOVERNAVAM ISRAEL|2 Crônicas|1-3;4-6;7-9;10-14;15-18;19-20;21-24;25-29;30-31;32-34;35-38
OS JUDEUS RETORNAM DO EXÍLIO|Esdras|1-3;4-7;8-10
OS JUDEUS RETORNAM DO EXÍLIO|Neemias|1-3;4-6;7-8;9-10;11-13
OS JUDEUS RETORNAM DO EXÍLIO|Ester|1-4;5-10
OS ESCRITOS DE MOISÉS|Jó|1-5;6-9;10-14;15-18;19-20;21-24;25-29;30-31;32-34;35-38;39-42
LIVROS DE CÂNTICOS E DE SABEDORIA PRÁTICA|Salmos|1-8;9-16;17-19;20-25;26-31;32-35;36-38;39-42;43-47;48-52;53-58;59-64;65-68;69-72;73-77;78-79;80-86;87-90;91-96;97-103;104-105;106-108;109-115;116-119:63;119:64-176;120-129;130-138;139-144;145-150
LIVROS DE CÂNTICOS E DE SABEDORIA PRÁTICA|Provérbios|1-4;5-8;9-12;13-16;17-19;20-22;23-27;28-31
LIVROS DE CÂNTICOS E DE SABEDORIA PRÁTICA|Eclesiastes|1-4;5-8;9-12
LIVROS DE CÂNTICOS E DE SABEDORIA PRÁTICA|Cântico de Salomão|1-8
OS PROFETAS|Isaías|1-4;5-7;8-10;11-14;15-19;20-24;25-28;29-31;32-35;36-37;38-40;41-43;44-47;48-50;51-55;56-58;59-62;63-66
OS PROFETAS|Jeremias|1-3;4-5;6-7;8-10;11-13;14-16;17-20;21-23;24-26;27-29;30-31;32-33;34-36;37-39;40-42
OS PROFETAS|Lamentações|1-2;3-5
OS PROFETAS|Ezequiel|1-3;4-6;7-9;10-12;13-15;16;17-18;19-21;22-23;24-26;27-28;29-31;32-33;34-36;37-38;39-40;41-43;44-45;46-48
OS PROFETAS|Daniel|1-2;3-4;5-7;8-10;11-12
OS PROFETAS|Oseias|1-7;8-14
OS PROFETAS|Joel|1-3
OS PROFETAS|Amós|1-5;6-9
OS PROFETAS|Obadias/Jonas|Livro completo
OS PROFETAS|Miqueias|1-7
OS PROFETAS|Naum/Habacuque|Livros completos
OS PROFETAS|Sofonias/Ageu|Livros completos
OS PROFETAS|Zacarias|1-7;8-11;12-14
OS PROFETAS|Malaquias|1-4
RELATOS DA VIDA E DO MINISTÉRIO DE JESUS|Mateus|1-4;5-7;8-10;11-13;14-17;18-20;21-23;24-25;26;27-28
RELATOS DA VIDA E DO MINISTÉRIO DE JESUS|Marcos|1-3;4-5;6-8;9-10;11-13;14-16
RELATOS DA VIDA E DO MINISTÉRIO DE JESUS|Lucas|1-2;3-5;6-7;8-9;10-11;12-13;14-16;17-19;20-22;23-24
RELATOS DA VIDA E DO MINISTÉRIO DE JESUS|João|1-3;4-5;6-7;8-9;10-12;13-15;16-18;19-21
CRESCIMENTO DA CONGREGAÇÃO CRISTÃ|Atos|1-3;4-6;7-8;9-11;12-14;15-16;17-19;20-21;22-23;24-26;27-28
AS CARTAS DE PAULO|Romanos|1-3;4-7;8-11;12-16
AS CARTAS DE PAULO|1 Coríntios|1-6;7-10;11-14;15-16
AS CARTAS DE PAULO|2 Coríntios|1-6;7-10;11-13
AS CARTAS DE PAULO|Gálatas|1-6
AS CARTAS DE PAULO|Efésios|1-6
AS CARTAS DE PAULO|Filipenses|1-4
AS CARTAS DE PAULO|Colossenses|1-4
AS CARTAS DE PAULO|1 Tessalonicenses|1-5
AS CARTAS DE PAULO|2 Tessalonicenses|1-3
AS CARTAS DE PAULO|1 Timóteo|1-6
AS CARTAS DE PAULO|2 Timóteo|1-4
AS CARTAS DE PAULO|Tito/Filêmon|Livros completos
AS CARTAS DE PAULO|Hebreus|1-6;7-10;11-13
ESCRITOS DE OUTROS APÓSTOLOS E DISCÍPULOS|Tiago|1-5
ESCRITOS DE OUTROS APÓSTOLOS E DISCÍPULOS|1 Pedro|1-5
ESCRITOS DE OUTROS APÓSTOLOS E DISCÍPULOS|2 Pedro|1-3
ESCRITOS DE OUTROS APÓSTOLOS E DISCÍPULOS|1 João|1-5
ESCRITOS DE OUTROS APÓSTOLOS E DISCÍPULOS|2 João/3 João/Judas|Livros completos
ESCRITOS DE OUTROS APÓSTOLOS E DISCÍPULOS|Apocalipse|1-4;5-9;10-14;15-18;19-22
""".trimIndent()

    val all: List<BibleReading> = source
        .lines()
        .filter { it.isNotBlank() }
        .flatMap { line ->
            val parts = line.split("|")
            val section = parts[0].trim()
            val book = parts[1].trim()
            parts[2].split(";").mapIndexed { index, chapters ->
                val normalizedChapters = chapters.trim()
                BibleReading(
                    id = "${book.lowercase().replace(" ", "_").replace("/", "_")}_${index + 1}",
                    section = section,
                    book = book,
                    chapters = normalizedChapters
                )
            }
        }
}

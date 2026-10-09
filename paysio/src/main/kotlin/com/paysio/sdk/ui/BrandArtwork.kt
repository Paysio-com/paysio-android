package com.paysio.sdk.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Card brand artwork, path for path the same as the React Native SDK's
 * `src/ui/CardBrand.tsx` (itself the web app's CardBrandIcon + nucleo-credit-cards).
 * Do not edit the path strings by hand: copy them from CardBrand.tsx.
 *
 * Every mark is a 28x18 card badge on a 32x32 canvas, cropped to the viewBox
 * `2 7 28 18` so the card fills the box edge to edge. The `<Rect>` elements of the
 * RN artwork are kept as rectangles ([ArtLayer.Rect]) and turned into path nodes
 * from their x / y / width / height / rx, exactly as SVG defines a rounded rect.
 */
internal sealed class ArtLayer {
    abstract val fill: Color
    abstract val alpha: Float

    data class Path(val d: String, override val fill: Color, override val alpha: Float = 1f) : ArtLayer()

    data class Rect(
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val rx: Float = 0f,
        override val fill: Color,
        override val alpha: Float = 1f,
    ) : ArtLayer()
}

internal object BrandArtwork {
    /** The viewBox crop: `2 7 28 18`. */
    const val VIEW_X = 2f
    const val VIEW_Y = 7f
    const val VIEW_W = 28f
    const val VIEW_H = 18f

    /** Height / width of every mark. */
    const val RATIO = 0.65f

    const val EDGE =
        "m27,7H5c-1.657,0-3,1.343-3,3v12c0,1.657,1.343,3,3,3h22c1.657,0,3-1.343,3-3v-12c0-1.657-1.343-3-3-3Zm2,15c0,1.103-.897,2-2,2H5c-1.103,0-2-.897-2-2v-12c0-1.103.897-2,2-2h22c1.103,0,2,.897,2,2v12Z"
    const val HIGHLIGHT =
        "m27,8H5c-1.105,0-2,.895-2,2v1c0-1.105.895-2,2-2h22c1.105,0,2,.895,2,2v-1c0-1.105-.895-2-2-2Z"

    private val White = Color(0xFFFFFFFF)
    private val Black = Color(0xFF000000)

    private fun hex(value: String): Color {
        val h = value.removePrefix("#")
        val full = if (h.length == 3) h.map { "$it$it" }.joinToString("") else h
        return Color(0xFF000000 or full.toLong(16))
    }

    /** The shared badge: rounded rect, 15 % edge, 20 % white top highlight. */
    private fun cardBase(fill: String, edge: Color = Black): List<ArtLayer> = listOf(
        ArtLayer.Rect(2f, 7f, 28f, 18f, 3f, hex(fill)),
        ArtLayer.Path(EDGE, edge, 0.15f),
        ArtLayer.Path(HIGHLIGHT, White, 0.2f),
    )

    private fun white(d: String) = ArtLayer.Path(d, White)

    val visa: List<ArtLayer> = cardBase("#1434cb") + listOf(
        white("m13.392,12.624l-2.838,6.77h-1.851l-1.397-5.403c-.085-.332-.158-.454-.416-.595-.421-.229-1.117-.443-1.728-.576l.041-.196h2.98c.38,0,.721.253.808.69l.738,3.918,1.822-4.608h1.84Z"),
        white("m20.646,17.183c.008-1.787-2.47-1.886-2.453-2.684.005-.243.237-.501.743-.567.251-.032.943-.058,1.727.303l.307-1.436c-.421-.152-.964-.299-1.638-.299-1.732,0-2.95.92-2.959,2.238-.011.975.87,1.518,1.533,1.843.683.332.912.545.909.841-.005.454-.545.655-1.047.663-.881.014-1.392-.238-1.799-.428l-.318,1.484c.41.188,1.165.351,1.947.359,1.841,0,3.044-.909,3.05-2.317"),
        white("m25.423,12.624h-1.494c-.337,0-.62.195-.746.496l-2.628,6.274h1.839l.365-1.011h2.247l.212,1.011h1.62l-1.415-6.77Zm-2.16,4.372l.922-2.542.53,2.542h-1.452Z"),
        white("M15.894 12.624L14.446 19.394 12.695 19.394 14.143 12.624 15.894 12.624z"),
    )

    fun mastercard(
        left: String = "#eb001b",
        right: String = "#f79e1b",
        centre: String = "#ff5f00",
        card: String = "#141413",
    ): List<ArtLayer> = cardBase(card) + listOf(
        ArtLayer.Rect(13.597f, 11.677f, 4.81f, 8.643f, 0f, hex(centre)),
        ArtLayer.Path(
            "m13.902,15.999c0-1.68.779-3.283,2.092-4.322-2.382-1.878-5.849-1.466-7.727.932-1.863,2.382-1.451,5.833.947,7.712,2,1.573,4.795,1.573,6.795,0-1.329-1.038-2.107-2.642-2.107-4.322Z",
            hex(left),
        ),
        ArtLayer.Path(
            "m24.897,15.999c0,3.039-2.459,5.497-5.497,5.497-1.237,0-2.428-.412-3.39-1.176,2.382-1.878,2.795-5.329.916-7.727-.275-.336-.58-.657-.916-.916,2.382-1.878,5.849-1.466,7.712.932.764.962,1.176,2.153,1.176,3.39Z",
            hex(right),
        ),
    )

    // The white slab goes down first and the wordmarks are knocked out of it in brand
    // blue, which is why the card edge is drawn last here, not first.
    val amex: List<ArtLayer> = run {
        val blue = hex("#0f70ce")
        listOf(
            ArtLayer.Rect(2f, 7f, 28f, 18f, 3f, blue),
            white("m27.026,9l-.719,1.965-.708-1.965h-3.885v2.582l-1.136-2.582h-3.119l-3.259,7.409h2.637v6.591h8.097l1.316-1.458,1.322,1.458h2.244c.112-.314.184-.647.184-1v-1.041l-1.58-1.698,1.58-1.655v-7.606c0-.353-.072-.686-.184-1h-2.79Z"),
            ArtLayer.Path("m17.679,14.433h2.61l.502,1.148h1.78l-2.531-5.754h-2.039l-2.531,5.754h1.734l.477-1.148Zm1.307-3.135l.775,1.844h-1.535l.761-1.844Z", blue),
            ArtLayer.Path("M22.542 9.827L25.018 9.827 26.302 13.39 27.604 9.827 30 9.827 30 15.581 28.45 15.581 28.45 11.603 26.977 15.581 25.608 15.581 24.124 11.631 24.124 15.581 22.542 15.581 22.542 9.827z", blue),
            ArtLayer.Path("M19.24 20.82L19.24 19.944 22.484 19.944 22.484 18.624 19.24 18.624 19.24 17.748 22.565 17.748 22.565 16.409 17.664 16.409 17.664 22.173 22.565 22.173 22.565 20.82 19.24 20.82z", blue),
            ArtLayer.Path("M24.638 16.409L26.271 18.234 27.968 16.409 30 16.409 27.283 19.254 30 22.173 27.939 22.173 26.249 20.309 24.567 22.173 22.537 22.173 25.272 19.275 22.537 16.409 24.638 16.409z", blue),
            ArtLayer.Path(EDGE, Black, 0.15f),
            ArtLayer.Path(HIGHLIGHT, White, 0.2f),
        )
    }

    val discover: List<ArtLayer> = cardBase("#f47922") + listOf(
        white("m5.081,14.116h-1.081v3.777h1.076c.572,0,.985-.135,1.348-.436.431-.357.686-.894.686-1.45,0-1.115-.833-1.891-2.027-1.891Zm.86,2.837c-.231.209-.532.3-1.008.3h-.198v-2.497h.198c.476,0,.765.085,1.008.305.255.227.408.578.408.94s-.153.725-.408.952Z"),
        white("M7.448 14.116H8.185V17.893H7.448z"),
        white("m9.986,15.565c-.442-.164-.572-.271-.572-.475,0-.238.231-.419.549-.419.221,0,.402.091.594.306l.386-.505c-.317-.277-.696-.419-1.11-.419-.668,0-1.178.464-1.178,1.082,0,.52.237.787.929,1.036.288.102.435.17.509.215.147.096.221.232.221.391,0,.306-.243.533-.572.533-.351,0-.634-.175-.804-.504l-.476.458c.339.498.747.719,1.308.719.766,0,1.303-.509,1.303-1.24,0-.6-.248-.872-1.086-1.178Z"),
        white("m11.305,16.007c0,1.11.872,1.971,1.994,1.971.317,0,.589-.062.924-.22v-.867c-.295.295-.555.414-.889.414-.742,0-1.269-.538-1.269-1.303,0-.725.543-1.297,1.234-1.297.351,0,.617.125.924.425v-.867c-.323-.164-.589-.232-.906-.232-1.116,0-2.011.878-2.011,1.976Z"),
        white("m18.461,16c0,1.105-.895,2-2,2s-2-.895-2-2,.895-2,2-2,2,.895,2,2Z"),
        white("M20.063 16.653L19.056 14.116 18.251 14.116 19.854 17.99 20.25 17.99 21.882 14.116 21.083 14.116 20.063 16.653z"),
        white("M22.215 17.893L24.304 17.893 24.304 17.253 22.951 17.253 22.951 16.234 24.254 16.234 24.254 15.594 22.951 15.594 22.951 14.756 24.304 14.756 24.304 14.116 22.215 14.116 22.215 17.893z"),
        white("m27.221,15.231c0-.707-.487-1.115-1.337-1.115h-1.092v3.777h.736v-1.517h.096l1.02,1.517h.906l-1.189-1.591c.555-.113.861-.492.861-1.071Zm-1.478.624h-.216v-1.144h.227c.459,0,.708.192.708.56,0,.38-.249.584-.72.584Z"),
    )

    val dinersClub: List<ArtLayer> = cardBase("#fff") + listOf(
        ArtLayer.Path(
            "m15.111,22c-3.32.015-6.052-2.674-6.052-5.948,0-3.578,2.732-6.053,6.052-6.052h1.556c3.28,0,6.274,2.473,6.274,6.052,0,3.273-2.994,5.948-6.274,5.948h-1.556Zm.014-11.504c-3.034.001-5.492,2.459-5.492,5.493,0,3.033,2.458,5.491,5.492,5.492,3.034,0,5.492-2.458,5.493-5.492,0-3.034-2.459-5.492-5.493-5.493m-1.247,8.741v-6.497c-1.305.503-2.231,1.766-2.235,3.249.003,1.483.929,2.746,2.235,3.248m4.729-3.248c-.003-1.484-.93-2.748-2.236-3.25v6.499c1.307-.502,2.234-1.766,2.236-3.25",
            hex("#0d4e96"),
        ),
    )

    // The three column blocks, drawn in the mid tone of each of the original gradients.
    val jcb: List<ArtLayer> = cardBase("#fff") + listOf(
        ArtLayer.Path(
            "m9.019,9c-1.282,0-2.333,1.038-2.333,2.333v5.756c.654.321,1.333.526,2.013.526.808,0,1.244-.487,1.244-1.154v-2.718h2v2.705c0,1.051-.654,1.91-2.872,1.91-1.346,0-2.397-.295-2.397-.295v4.91h3.423c1.282,0,2.333-1.038,2.333-2.333v-11.641s-3.41,0-3.41,0Z",
            hex("#0058a0"),
        ),
        ArtLayer.Path(
            "m15.468,9c-1.282,0-2.333,1.038-2.333,2.333v3.051c.59-.5,1.615-.821,3.269-.744.885.038,1.833.282,1.833.282v.987c-.474-.244-1.038-.462-1.769-.513-1.256-.09-2.013.526-2.013,1.603,0,1.09.756,1.705,2.013,1.603.731-.051,1.295-.282,1.769-.513v.987s-.936.244-1.833.282c-1.654.077-2.679-.244-3.269-.744v5.385h3.423c1.282,0,2.333-1.038,2.333-2.333v-11.667s-3.423,0-3.423,0Z",
            hex("#c01c33"),
        ),
        ArtLayer.Path(
            "m21.917,9c-1.282,0-2.333,1.038-2.333,2.333v2.423h3.295c.077,0,.167,0,.231.013.744.038,1.295.423,1.295,1.09,0,.526-.372.974-1.064,1.064v.026c.756.051,1.333.474,1.333,1.128,0,.705-.641,1.167-1.487,1.167h-3.615v4.744h3.423c1.282,0,2.333-1.038,2.333-2.333v-11.654h-3.41,0Z",
            hex("#28963c"),
        ),
    )

    val unionPay: List<ArtLayer> = cardBase("#fff") + listOf(
        ArtLayer.Path(
            "m9.193,9h5.586c.78,0,1.265.636,1.083,1.419l-2.601,11.165c-.184.781-.964,1.416-1.744,1.416h-5.586c-.78,0-1.265-.636-1.083-1.416l2.602-11.165c.181-.783.961-1.419,1.743-1.419",
            hex("#ed171f"),
        ),
        ArtLayer.Path(
            "m14.314,9h6.424c.78,0,.428.636.245,1.419l-2.601,11.165c-.182.781-.125,1.416-.906,1.416h-6.423c-.781,0-1.265-.636-1.081-1.416l2.6-11.165c.184-.783.963-1.419,1.743-1.419",
            hex("#082f67"),
        ),
        ArtLayer.Path(
            "m20.484,9h5.585c.78,0,1.265.636,1.082,1.419l-2.601,11.165c-.183.781-.963,1.416-1.745,1.416h-5.584c-.782,0-1.266-.636-1.082-1.416l2.6-11.165c.183-.783.963-1.419,1.745-1.419",
            hex("#006a65"),
        ),
    )

    val applePay: List<ArtLayer> = run {
        val ink = hex("#1a1a1a")
        cardBase("#e6e6e6") + listOf(
            ArtLayer.Path("m8.8469,12.8798c.2708-.3387.4545-.7935.4061-1.2582-.3964.0197-.8801.2615-1.1602.6005-.2515.2903-.474.7641-.416,1.2093.445.0386.8895-.2224,1.1701-.5516", ink),
            ArtLayer.Path("m9.2479,13.5184c-.6462-.0385-1.1956.3668-1.5042.3668-.3088,0-.7813-.3474-1.2924-.338-.6652.0098-1.2825.3859-1.6201.9841-.6944,1.1968-.1832,2.972.492,3.9467.3279.4822.7231,1.0132,1.2438.9941.492-.0193.6848-.3186,1.2828-.3186.5976,0,.7713.3186,1.2921.3089.5401-.0097.8777-.4825,1.2056-.9651.3762-.5497.5302-1.0805.5398-1.1096-.0097-.0097-1.0414-.4055-1.051-1.5923-.0097-.9937.81-1.4664.8486-1.4957-.4629-.6847-1.1863-.7619-1.437-.7813", ink),
            ArtLayer.Path("m14.8746,12.1735c1.4045,0,2.3825.9681,2.3825,2.3777,0,1.4146-.9981,2.3878-2.4178,2.3878h-1.5551v2.473h-1.1235v-7.2385h2.7138Zm-1.5903,3.8224h1.2892c.9782,0,1.535-.5266,1.535-1.4396s-.5567-1.4346-1.5299-1.4346h-1.2942v2.8743Z", ink),
            ArtLayer.Path("m17.5507,17.9121c0-.9231.7073-1.4899,1.9615-1.5601l1.4446-.0852v-.4063c0-.5869-.3963-.9381-1.0583-.9381-.6272,0-1.0185.3009-1.1137.7725h-1.0233c.0602-.9532.8727-1.6554,2.177-1.6554,1.2791,0,2.0967.6772,2.0967,1.7356v3.6368h-1.0384v-.8678h-.025c-.3059.5869-.9732.9581-1.6654.9581-1.0334,0-1.7557-.6421-1.7557-1.5901Zm3.406-.4765v-.4163l-1.2993.0802c-.6471.0452-1.0132.3311-1.0132.7826,0,.4614.3812.7624.9631.7624.7574,0,1.3494-.5217,1.3494-1.2089Z", ink),
            ArtLayer.Path("m23.0155,21.3533v-.8779c.0801.02.2607.02.351.02.5016,0,.7725-.2106.938-.7524,0-.0101.0954-.321.0954-.3261l-1.9061-5.2821h1.1737l1.3345,4.294h.0199l1.3345-4.294h1.1437l-1.9765,5.553c-.4513,1.2792-.973,1.6905-2.0665,1.6905-.0904,0-.3613-.0101-.4414-.0251Z", ink),
        )
    }

    val googlePay: List<ArtLayer> = run {
        val grey = hex("#5f6368")
        cardBase("#fff") + listOf(
            ArtLayer.Path("m14.8969,16.1308v2.7891h-.8997v-6.8978h2.3393c.5698,0,1.1097.2099,1.5295.5998.4199.3599.6298.8997.6298,1.4695s-.2099,1.0797-.6298,1.4695c-.4199.3899-.9297.5998-1.5295.5998l-1.4395-.03h0Zm0-3.269v2.3992h1.4995c.3299,0,.6598-.12.8697-.3599.4798-.4499.4798-1.1996.03-1.6495l-.03-.03c-.2399-.2399-.5398-.3899-.8697-.3599h-1.4995Z", grey),
            ArtLayer.Path("m20.5651,14.0615c.6598,0,1.1696.1799,1.5595.5398s.5698.8397.5698,1.4396v2.8791h-.8397v-.6598h-.03c-.3599.5398-.8697.8097-1.4695.8097-.5098,0-.9597-.15-1.3196-.4499-.3299-.2999-.5398-.7198-.5398-1.1696,0-.4799.1799-.8697.5398-1.1696s.8697-.4199,1.4695-.4199c.5398,0,.9597.09,1.2896.2999v-.2099c0-.2999-.12-.5998-.3599-.7798-.2399-.2099-.5398-.3299-.8697-.3299-.5098,0-.8997.2099-1.1696.6298l-.7798-.4799c.4799-.6298,1.1097-.9297,1.9494-.9297Zm-1.1396,3.4189c0,.2399.12.4499.2999.5698.2099.15.4499.2399.6898.2399.3599,0,.7198-.15.9897-.4199.2999-.2699.4499-.5998.4499-.9597-.2699-.2099-.6598-.3299-1.1696-.3299-.3599,0-.6598.09-.8997.2699-.2399.15-.3599.3599-.3599.6298Z", grey),
            ArtLayer.Path("m27.5529,14.2114l-2.9691,6.8079h-.8997l1.1097-2.3693-1.9494-4.4086h.9597l1.4096,3.3889h.03l1.3796-3.3889h.9297v-.03Z", grey),
            ArtLayer.Path("m12.2324,15.531c0-.2699-.03-.5398-.06-.8097h-3.7488v1.5295h2.1293c-.09.4799-.3599.9297-.7798,1.1996v.9897h1.2896c.7498-.6898,1.1696-1.7095,1.1696-2.9091Z", hex("#4285f4")),
            ArtLayer.Path("m8.4236,19.3998c1.0797,0,1.9794-.3599,2.6392-.9597l-1.2896-.9897c-.3599.2399-.8097.3899-1.3496.3899-1.0197,0-1.9194-.6898-2.2193-1.6495h-1.3196v1.0197c.6898,1.3496,2.0394,2.1893,3.5389,2.1893Z", hex("#34a853")),
            ArtLayer.Path("m6.2043,16.1908c-.1799-.4799-.1799-1.0197,0-1.5295v-1.0197h-1.3196c-.5698,1.1097-.5698,2.4292,0,3.5689l1.3196-1.0197Z", hex("#fbbc04")),
            ArtLayer.Path("m8.4236,13.0418c.5698,0,1.1097.2099,1.5295.5998h0l1.1396-1.1396c-.7198-.6598-1.6795-1.0497-2.6392-1.0197-1.4995,0-2.8791.8397-3.5389,2.1893l1.3196,1.0197c.2699-.9597,1.1696-1.6495,2.1893-1.6495Z", hex("#ea4335")),
        )
    }

    val bank: List<ArtLayer> = run {
        val ink = hex("#1a1a1a")
        cardBase("#e6e6e6") + listOf(
            ArtLayer.Path("m21.5,21h-11c-.2764,0-.5.2236-.5.5s.2236.5.5.5h11c.2764,0,.5-.2236.5-.5s-.2236-.5-.5-.5Z", ink, 0.5f),
            ArtLayer.Path("m21.6694,13.6787l-4.8486-3.2324c-.499-.332-1.1426-.332-1.6416,0l-4.8491,3.2324c-.2661.1777-.3823.5029-.2896.8096.0928.3062.3701.5117.6899.5117h1.0195v4h-.75c-.2764,0-.5.2236-.5.5s.2236.5.5.5h10c.2764,0,.5-.2236.5-.5s-.2236-.5-.5-.5h-.75v-4h1.0195c.3198,0,.5972-.2056.6899-.5117.0928-.3066-.0234-.6318-.29-.8096Zm-7.4194,5.3213h-1.5v-4h1.5v4Zm2.5,0h-1.5v-4h1.5v4Zm-.75-5.5c-.4142,0-.75-.3358-.75-.75s.3358-.75.75-.75.75.3358.75.75-.3358.75-.75.75Zm3.25,5.5h-1.5v-4h1.5v4Z", ink, 0.5f),
        )
    }

    /**
     * No brand resolved. Neutral rather than wrong: a grey card says "a card we don't
     * recognise", which is true, where guessing a brand would not be.
     */
    fun genericCard(color: Color): List<ArtLayer> = listOf(
        ArtLayer.Rect(2f, 7f, 28f, 18f, 3f, color, 0.16f),
        ArtLayer.Path(EDGE, color, 0.32f),
        ArtLayer.Rect(5f, 13f, 8f, 5.5f, 1f, color, 0.4f),
        ArtLayer.Rect(5f, 20.5f, 12f, 1.6f, 0.8f, color, 0.4f),
    )

    /** SVG `<rect rx>` as path nodes (rx = ry, clamped to half the shorter side as SVG does). */
    private fun rectNodes(r: ArtLayer.Rect): List<PathNode> {
        val rx = r.rx.coerceAtMost(minOf(r.width, r.height) / 2f)
        val x = r.x
        val y = r.y
        val w = r.width
        val h = r.height
        return PathBuilder().apply {
            if (rx <= 0f) {
                moveTo(x, y); horizontalLineTo(x + w); verticalLineTo(y + h); horizontalLineTo(x); close()
            } else {
                moveTo(x + rx, y)
                horizontalLineTo(x + w - rx)
                arcTo(rx, rx, 0f, false, true, x + w, y + rx)
                verticalLineTo(y + h - rx)
                arcTo(rx, rx, 0f, false, true, x + w - rx, y + h)
                horizontalLineTo(x + rx)
                arcTo(rx, rx, 0f, false, true, x, y + h - rx)
                verticalLineTo(y + rx)
                arcTo(rx, rx, 0f, false, true, x + rx, y)
                close()
            }
        }.nodes
    }

    private val cache = HashMap<String, ImageVector>()

    /** Builds (and caches under [key]) the 28x18 vector for a layer list. */
    fun vector(key: String, layers: () -> List<ArtLayer>): ImageVector = synchronized(cache) {
        cache.getOrPut(key) {
            ImageVector.Builder(
                name = "paysio-brand-$key",
                defaultWidth = VIEW_W.dp,
                defaultHeight = VIEW_H.dp,
                viewportWidth = VIEW_W,
                viewportHeight = VIEW_H,
            ).apply {
                // viewBox "2 7 28 18": shift the 32x32 canvas so (2, 7) lands at the origin.
                addGroup(name = "viewBox", translationX = -VIEW_X, translationY = -VIEW_Y)
                layers().forEach { layer ->
                    val nodes = when (layer) {
                        is ArtLayer.Path -> PathParser().parsePathString(layer.d).toNodes()
                        is ArtLayer.Rect -> rectNodes(layer)
                    }
                    addPath(pathData = nodes, fill = SolidColor(layer.fill), fillAlpha = layer.alpha)
                }
                clearGroup()
            }.build()
        }
    }
}

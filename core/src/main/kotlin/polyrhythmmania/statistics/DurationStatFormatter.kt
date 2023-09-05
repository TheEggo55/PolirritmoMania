package polyrhythmmania.statistics

import paintbox.binding.ReadOnlyIntVar
import paintbox.binding.ReadOnlyVar
import paintbox.binding.Var
import paintbox.i18n.ILocalization
import paintbox.util.DecimalFormats
import polyrhythmmania.Localization

class DurationStatFormatter(
    val localizationKey: String,
    val localizationBase: ILocalization = Localization,
) : StatFormatter {

    companion object {

        val DEFAULT: DurationStatFormatter = DurationStatFormatter("statistics.formatter.playTime", Localization)
    }

    override fun format(value: ReadOnlyIntVar): ReadOnlyVar<String> {
        return localizationBase.getVar(localizationKey, Var {
            val totalSeconds = value.use()
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds / 60) % 60
            val seconds = totalSeconds % 60
            val decimalFormat = DecimalFormats["00"]
            listOf(
                decimalFormat.format(hours.toLong()),
                decimalFormat.format(minutes.toLong()),
                decimalFormat.format(seconds.toLong())
            )
        })
    }
}
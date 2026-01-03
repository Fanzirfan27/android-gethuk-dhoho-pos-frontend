package nuril.irfan.gethukdhonopos

import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.ktx.Firebase
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.formatter.ValueFormatter
import nuril.irfan.gethukdhonopos.adapter.TransaksiAdapter
import nuril.irfan.gethukdhonopos.databinding.ActivityAdminTransaksiBinding
import nuril.irfan.gethukdhonopos.model.Transaksi
import java.util.Calendar
import java.util.Locale

class AdminTransaksiActivity : AppCompatActivity() {

    private lateinit var b: ActivityAdminTransaksiBinding
    private val db by lazy { Firebase.firestore }

    private lateinit var adapter: TransaksiAdapter
    private var startDay: Calendar = Calendar.getInstance().apply { setToStartOfDay() }
    private var endDay: Calendar = Calendar.getInstance().apply { setToStartOfDay() } // simpan sebagai awal hari
    private var lastKeyword: String = ""
    private var lastData: List<Transaksi> = emptyList()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityAdminTransaksiBinding.inflate(layoutInflater)
        setContentView(b.root)

        setSupportActionBar(b.toolbar)
        b.toolbar.setNavigationOnClickListener { finish() }

        adapter = TransaksiAdapter(
            onLihat = { openDetail(it) },
            onHapus = { confirmDelete(it) }
        )

        b.rvTransaksi.layoutManager = LinearLayoutManager(this)
        b.rvTransaksi.adapter = adapter

        b.etCari.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                lastKeyword = v.text?.toString()?.trim().orEmpty()
                loadData()
                true
            } else false
        }

        b.btnTanggal.setOnClickListener { pickDateRangeOnce() }
        b.btnRefresh.setOnClickListener { loadData() }

        updateTanggalBtnText()
        loadData()
        loadChartHarian()
        b.btnExportPdf.setOnClickListener {
            if (lastData.isEmpty()) {
                toast("Tidak ada data untuk diexport")
            } else {
                val fileName =
                    "Laporan_Penjualan_${System.currentTimeMillis()}.pdf"
                createPdfLauncher.launch(fileName)
            }
        }



    }
    private val createPdfLauncher =
        registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/pdf")
        ) { uri ->
            if (uri != null) {
                writePdfToUri(uri, lastData)
            } else {
                toast("Penyimpanan dibatalkan")
            }
        }

    /* ================== UI helpers ================== */

    private fun updateTanggalBtnText() {
        val s = "%1\$td/%1\$tm/%1\$tY".format(startDay)
        val e = "%1\$td/%1\$tm/%1\$tY".format(endDay)
        b.btnTanggal.text = if (s == e) "Hari ini ($s)" else "$s → $e"
    }

    private fun setLoading(on: Boolean) {
        b.progress.visibility = if (on) android.view.View.VISIBLE else android.view.View.GONE
        b.tvEmpty.visibility = android.view.View.GONE
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    /* ================== Date Range Picker SEKALI ================== */

    private fun pickDateRangeOnce() {
        val constraints = CalendarConstraints.Builder().build()
        val selection = androidx.core.util.Pair(
            startDay.timeInMillis,
            endDay.timeInMillis
        )

        val picker = MaterialDatePicker.Builder
            .dateRangePicker()
            .setTitleText("Pilih rentang tanggal")
            .setCalendarConstraints(constraints)
            .setSelection(selection)
            .build()

        picker.addOnPositiveButtonClickListener { range ->
            // simpan sebagai awal hari
            startDay.timeInMillis = range.first!!
            startDay.setToStartOfDay()

            endDay.timeInMillis = range.second!!
            endDay.setToStartOfDay()

            updateTanggalBtnText()
            loadData()
        }

        picker.show(supportFragmentManager, "date_range")
    }

    /* ================== Data loading ================== */

    private fun loadData() {


        setLoading(true)

        val startTs = Timestamp(startDay.time)
        // endExclusive = awal hari end + 1 → agar inklusif utk tanggal akhir
        val endExclusiveCal = (endDay.clone() as Calendar).apply { add(Calendar.DATE, 1) }
        val endExclusive = Timestamp(endExclusiveCal.time)

        db.collection("transaksi")
            .whereGreaterThanOrEqualTo("tanggal", startTs)
            .whereLessThan("tanggal", endExclusive)
            .orderBy("tanggal", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { qs ->
                var list = qs.documents.map { d ->
                    Transaksi(
                        id = d.id,
                        tanggal = d.getTimestamp("tanggal"),
                        kasir_uid = d.getString("kasir_uid") ?: "",
                        metode_bayar = d.getString("metode_bayar") ?: "",
                        subtotal = d.getLong("subtotal") ?: 0L,
                        diskon = d.getLong("diskon") ?: 0L,
                        total = d.getLong("total") ?: 0L,
                        tunai_diterima = d.getLong("tunai_diterima") ?: 0L,
                        kembalian = d.getLong("kembalian") ?: 0L,
                        status = d.getString("status") ?: "",
                        shift_id = d.getString("shift_id") ?: ""
                    )
                }
                if (lastKeyword.isNotBlank()) {
                    val kw = lastKeyword.lowercase(Locale.ROOT)
                    list = list.filter {
                        it.kasir_uid.lowercase(Locale.ROOT).contains(kw) ||
                                it.id.lowercase(Locale.ROOT).contains(kw)
                    }
                }

                lastData = list
                adapter.submit(list)
                setLoading(false)
                b.tvEmpty.visibility = if (list.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            }
            .addOnFailureListener {
                setLoading(false)
                toast("Gagal memuat data: ${it.message}")
            }
    }

    /* ================== Actions ================== */

    private fun openDetail(t: Transaksi) {
        startActivity(
            Intent(this, AdminTransaksiDetailActivity::class.java)
                .putExtra("TRX_ID", t.id)
        )
    }

    private fun confirmDelete(t: Transaksi) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Hapus transaksi?")
            .setMessage("TRX-${t.id.takeLast(6)} total Rp %,d.\nHapus permanen?\n\nKembalikan stok barang juga?"
                .format(t.total))
            .setNegativeButton("Batal", null)
            .setNeutralButton("Hapus saja") { _, _ -> deleteTransaction(t, restoreStock = false) }
            .setPositiveButton("Hapus + kembalikan stok") { _, _ -> deleteTransaction(t, restoreStock = true) }
            .show()
    }

    private fun deleteTransaction(t: Transaksi, restoreStock: Boolean) {
        setLoading(true)
        val trxRef = db.collection("transaksi").document(t.id)

        // 1) Ambil items dulu
        trxRef.collection("detail_transaksi").get()
            .addOnSuccessListener { qsItems ->
                val batch = db.batch()

                // 2) kalau restore stok → increment stok tiap produk
                if (restoreStock) {
                    qsItems.documents.forEach { d ->
                        val produkId = d.getString("produk_id") ?: return@forEach
                        val qty = d.getLong("qty") ?: 0L
                        val pRef = db.collection("produk").document(produkId)
                        batch.update(pRef, "stok", FieldValue.increment(qty))
                    }
                }

                // 3) hapus subcollection items
                qsItems.documents.forEach { d -> batch.delete(d.reference) }

                // 4) hapus dokumen transaksi
                batch.delete(trxRef)

                // 5) commit
                batch.commit()
                    .addOnSuccessListener {
                        Snackbar.make(b.rvTransaksi, "Transaksi dihapus", Snackbar.LENGTH_SHORT).show()
                        loadData()
                    }
                    .addOnFailureListener {
                        setLoading(false)
                        toast("Gagal hapus: ${it.message}")
                    }
            }
            .addOnFailureListener {
                setLoading(false)
                toast("Gagal ambil items: ${it.message}")
            }
    }
    private fun loadChartHarian() {
        val cal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -6)
            setToStartOfDay()
        }

        val startTs = Timestamp(cal.time)

        db.collection("transaksi")
            .whereGreaterThanOrEqualTo("tanggal", startTs)
            .get()
            .addOnSuccessListener { qs ->

                val map = linkedMapOf<String, Long>()

                // 1️⃣ Isi map dulu
                qs.documents.forEach { d ->
                    val ts = d.getTimestamp("tanggal") ?: return@forEach
                    val total = d.getLong("total") ?: 0L

                    val key = "%1\$td/%1\$tm".format(ts.toDate())
                    map[key] = (map[key] ?: 0L) + total
                }

                val labels = map.keys.toList()

                val entries = map.values.mapIndexed { index, value ->
                    Entry(index.toFloat(), value.toFloat())
                }

                val dataSet = LineDataSet(entries, "Omzet Harian").apply {
                    color = resources.getColor(android.R.color.holo_blue_dark)
                    valueTextSize = 10f
                    setCircleColor(color)
                    lineWidth = 2f
                }

                b.chartOmzet.data = LineData(dataSet)

                b.chartOmzet.xAxis.valueFormatter =
                    object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            val i = value.toInt()
                            return if (i in labels.indices) labels[i] else ""
                        }
                    }

                b.chartOmzet.xAxis.position = XAxis.XAxisPosition.BOTTOM
                b.chartOmzet.xAxis.granularity = 1f
                b.chartOmzet.axisLeft.axisMinimum = 0f
                b.chartOmzet.axisRight.isEnabled = false
                b.chartOmzet.description.text = "7 Hari Terakhir"

                b.chartOmzet.invalidate()
            }
    }
    private fun writePdfToUri(
        uri: android.net.Uri,
        data: List<Transaksi>
    ) {
        try {
            val output = contentResolver.openOutputStream(uri) ?: return

            val document = com.itextpdf.text.Document(
                com.itextpdf.text.PageSize.A4,
                36f, 36f, 36f, 36f
            )

            val writer = com.itextpdf.text.pdf.PdfWriter
                .getInstance(document, output)

            document.open()

            val titleFont = com.itextpdf.text.Font(
                com.itextpdf.text.Font.FontFamily.HELVETICA,
                16f,
                com.itextpdf.text.Font.BOLD
            )

            val normalFont = com.itextpdf.text.Font(
                com.itextpdf.text.Font.FontFamily.HELVETICA,
                10f
            )

            document.add(com.itextpdf.text.Paragraph(
                "LAPORAN PENJUALAN GETHUK DHOHO", titleFont
            ))

            document.add(com.itextpdf.text.Paragraph(" "))

            val periode =
                "%1\$td/%1\$tm/%1\$tY - %2\$td/%2\$tm/%2\$tY"
                    .format(startDay, endDay)

            document.add(com.itextpdf.text.Paragraph(
                "Periode: $periode", normalFont
            ))

            document.add(com.itextpdf.text.Paragraph(" "))

            val table = com.itextpdf.text.pdf.PdfPTable(5)
            table.widthPercentage = 100f
            table.setWidths(floatArrayOf(1f, 2f, 2f, 2f, 2f))

            fun cell(text: String) =
                com.itextpdf.text.pdf.PdfPCell(
                    com.itextpdf.text.Phrase(text, normalFont)
                ).apply { setPadding(6f) }

            table.addCell(cell("No"))
            table.addCell(cell("ID"))
            table.addCell(cell("Tanggal"))
            table.addCell(cell("Metode"))
            table.addCell(cell("Total"))

            var totalOmzet = 0L

            data.forEachIndexed { i, t ->
                table.addCell(cell("${i + 1}"))
                table.addCell(cell(t.id.takeLast(6)))
                table.addCell(cell(t.tanggal?.toDate()?.toString() ?: "-"))
                table.addCell(cell(t.metode_bayar))
                table.addCell(cell("Rp %,d".format(t.total)))
                totalOmzet += t.total
            }

            document.add(table)
            document.add(com.itextpdf.text.Paragraph(" "))

            document.add(com.itextpdf.text.Paragraph(
                "Total Omzet: Rp %,d".format(totalOmzet),
                titleFont
            ))

            document.close()
            writer.close()
            output.close()

            toast("PDF berhasil disimpan")
        } catch (e: Exception) {
            e.printStackTrace()
            toast("Gagal membuat PDF: ${e.message}")
        }
    }




}

/* ============== Calendar helpers ============== */
private fun Calendar.setToStartOfDay() {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}

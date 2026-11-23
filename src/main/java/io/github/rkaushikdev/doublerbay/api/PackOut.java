package io.github.rkaushikdev.doublerbay.api;

import java.time.LocalDate;
import java.util.List;

/**
 * One tail's work pack for one check, in its blocks: what is due, what is waiting, and the stripping
 * the inspections need.
 *
 * @param serial the tail
 * @param opens the first day of the check
 * @param closes the last day of the check
 * @param days how many days the check runs, both ends counted
 * @param total every line on the pack
 * @param overrun how many of the lines the check can clear were already over
 * @param deferrals the deferrals falling due
 * @param inspections the repeat inspections falling due
 * @param waiting the orders out to the manufacturer
 * @param stripping the panels the paint comes off before their inspections
 * @param references every reference the pack names
 */
public record PackOut(
        String serial,
        LocalDate opens,
        LocalDate closes,
        int days,
        int total,
        int overrun,
        List<PackLineOut> deferrals,
        List<PackLineOut> inspections,
        List<PackLineOut> waiting,
        List<PackLineOut> stripping,
        List<String> references) {}

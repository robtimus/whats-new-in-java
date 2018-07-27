(function ($) {

    var currentFilter = '';

    var matchesFilter = function (element, filter) {
        var filterValue = element.dataset.filterValue;
        return filter.startsWith('=') ? filterValue === filter.substring(1).trim() : filterValue.toLowerCase().indexOf(filter.toLowerCase()) !== -1;
    };

    var hasFilterMatches = function (element, selector, filter) {
        return $(element).find(selector).filter(function () {
            return matchesFilter(this, filter);
        }).length > 0;
    };

    var applyFilter = function (filter) {
        // note: this method cannot use :visible or :hidden selectors because these return false for elements that are nested in hidden elements

        clearFilter();

        // hide all non-matching packages and classes
        $('.panel.java-package, .panel.java-class').filter(function () {
            return !matchesFilter(this, filter);
        }).hide();
        // show all packages with non-hidden classes, as these may have gotten hidden
        $('.panel.java-package').filter(function () {
            return $(this).find('.panel.java-class').filter(function () {
                return matchesFilter(this, filter);
            }).length > 0;
        }).show();
        // show all classes for packages that match exactly
        if (filter.startsWith('=')) {
            $('.panel.java-package').filter(function () {
                return matchesFilter(this, filter)
            }).find('.java-class').show();
        }
        // hide all versions with non-visible packages
        $('.panel.java-version').filter(function () {
            return $(this).find('.panel.java-package').filter(function () {
                return matchesFilter(this, filter) || hasFilterMatches(this, '.panel.java-class', filter)
            }).length === 0
        }).hide();

        $('#filter-content').show().find('#current-filter').text(filter);
        currentFilter = filter;
        localStorage && localStorage.setItem('wnij-current-filter', filter);
    };

    var clearFilter = function () {
        $('.panel.java-version, .panel.java-package, .panel.java-class').show();
        $('#filter-content').hide().find('#current-filter').text('');
        currentFilter = '';
        localStorage && localStorage.removeItem('wnij-current-filter');
    };

    $(function () {
        $('a[rel=external').attr('target', '_blank');

        $('#collapse-all').click(function () {
            $('a[data-toggle=collapse][role=button][aria-expanded=true]').click();
        });
        $('#expand-all').click(function () {
            // only expand filter results
            $('a[data-toggle=collapse][role=button][aria-expanded=false]').filter(function () {
                return $(this).closest('.panel').css('display') !== 'none';
            }).click();
        });

        $('#filter').click(function () {
            $('#filter-value').val(currentFilter);
            $('#filter-dialog').modal({
                backdrop: 'static',
                show:     true
            });
        });

        $('#clear-filter').click(clearFilter);

        $('#filter-dialog').on('shown.bs.modal', function () {
            $(this).find('#filter-value').focus();
        });

        var applyFilterAndCloseDialog = function () {
            var filter = $('#filter-value').val().trim();
            if (filter) {
                applyFilter(filter);
            } else {
                clearFilter();
            }
            $('.modal').modal('hide');
        };
        $('#apply-filter').click(function () {
            applyFilterAndCloseDialog();
        });
        $('#filter-value').keyup(function (event) {
            if (event.keycode === 13 || event.which === 13) {
                applyFilterAndCloseDialog();
            }
        });

        currentFilter = (localStorage && localStorage.getItem('wnij-current-filter')) || '';
        if (currentFilter) {
            applyFilter(currentFilter);
        }
    });
})(jQuery);

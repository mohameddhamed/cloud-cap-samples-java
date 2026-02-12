namespace my.bookshop;

using {Currency, sap, managed, cuid} from '@sap/cds/common';
using my.bookshop.Books from './books';

entity Shelves: cuid, managed {
    name        : localized String(250) @mandatory;
    capacity : Integer @mandatory default 10 not null; // default 10 and > 0

//     ShelfBooks becomes contained
//     Contained entities cannot exist independently
//     Therefore:
//       ❌ no /ShelfBooks despite projection in service
//       ✅ only /Shelves(...)/bookAssignments
//     CAP enforces this even if you explicitly project the entity.
    bookAssignments       : Composition of many ShelfBooks on bookAssignments.shelf = $self;
}

entity ShelfBooks : cuid, managed {
    shelf : Association to Shelves not null;
    book  : Association to Books not null;
}

